package com.example.shoptests.integration;

import com.example.shoptests.model.*;
import com.example.shoptests.repository.OrderRepository;
import com.example.shoptests.repository.ProductRepository;
import com.example.shoptests.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("КТ-7: Гибридное интеграционное тестирование (интерфейс + бизнес-логика + данные)")
class HybridIntegrationTest {


    static class StubEmailProvider extends EmailService {
        final List<Email> captured = new ArrayList<>();
        @Override
        public void sendOrderConfirmation(String to, Order order) {
            captured.add(new Email(to, "StubSubject", "StubBody"));
        }
        @Override
        public List<Email> getSent() { return List.copyOf(captured); }
    }

    static class StubPaymentProvider extends PaymentGateway {
        Result nextResult = Result.SUCCESS;
        int calls = 0;
        @Override
        public Result process(PaymentDetails details, double amount) {
            calls++;
            return nextResult;
        }
    }

    static Address validAddress() { return new Address("Москва", "Тверская 1", "101000"); }
    static PaymentDetails validCard() {
        return PaymentDetails.card("4111111111111111", "12/30", "123");
    }


    @Nested
    @DisplayName("Уровень 1: полный сквозной стек — все модули реальные")
    class Level1_FullRealStack {

        ShopFacade facade;
        ProductRepository productRepo;
        OrderRepository orderRepo;
        EmailService email;
        LogService log;
        Product laptop;

        @BeforeEach
        void setUp() {
            productRepo = new ProductRepository();
            orderRepo = new OrderRepository();
            email = new EmailService();
            log = new LogService();
            facade = new ShopFacade(productRepo, orderRepo,
                    new PaymentGateway(), email, log);
            laptop = productRepo.save("Ноутбук ASUS", "Электроника", 50000, 10);
        }

        @Test
        @DisplayName("H-01: полный путь через фасад — поиск → корзина → заказ")
        void h01_fullPathThroughFacade() {
            List<Product> found = facade.search(new ShopFacade.SearchRequest(
                    "ASUS", "Электроника", 1000.0, 100000.0,
                    SearchService.SortBy.PRICE_ASC));
            assertEquals(1, found.size(), "Поиск должен найти ноутбук");

            facade.addToCart(1L, laptop.id(), 2);
            assertEquals(50000 * 2 * 1.20, facade.getCartTotal(1L), 0.01);

            Order order = facade.checkout(new ShopFacade.CheckoutRequest(
                    1L, "user@shop.com", validCard(), validAddress()));

            assertEquals(OrderStatus.PAID, order.getStatus());
            assertEquals(8, productRepo.findById(laptop.id()).orElseThrow().stock(),
                    "Склад реально уменьшился");
            assertEquals(1, email.getSent().size(), "Email реально отправлен");
            assertEquals(1, orderRepo.findAll().size(), "Заказ реально сохранён");
            assertTrue(log.getEntries().stream().anyMatch(e -> e.result().equals("success")));
        }

        @Test
        @DisplayName("H-02: полный путь с промокодом через все слои")
        void h02_promoThroughFacade() {
            facade.addToCart(1L, laptop.id(), 1);
            facade.applyPromoCode(1L, "SALE10");

            double expected = 50000 * 0.9 * 1.20;
            assertEquals(expected, facade.getCartTotal(1L), 0.01);

            Order order = facade.checkout(new ShopFacade.CheckoutRequest(
                    1L, "u@shop.com", validCard(), validAddress()));

            assertEquals(expected, order.getTotal(), 0.01);
            assertEquals(OrderStatus.PAID, order.getStatus());
        }
    }


    @Nested
    @DisplayName("Уровень 2: гибрид — реальный OrderService, стаб PaymentGateway")
    class Level2_HybridPayment {

        ShopFacade facade;
        ProductRepository productRepo;
        OrderRepository orderRepo;
        EmailService email;
        LogService log;
        StubPaymentProvider stubPayment;
        Product laptop;

        @BeforeEach
        void setUp() {
            productRepo = new ProductRepository();
            orderRepo = new OrderRepository();
            email = new EmailService();
            log = new LogService();
            stubPayment = new StubPaymentProvider();   // <-- стаб
            facade = new ShopFacade(productRepo, orderRepo,
                    stubPayment, email, log);
            laptop = productRepo.save("Ноутбук ASUS", "Электроника", 50000, 10);
        }

        @Test
        @DisplayName("H-03: гибрид — успешная оплата через стаб, всё остальное реальное")
        void h03_hybridSuccess() {
            facade.addToCart(1L, laptop.id(), 1);
            stubPayment.nextResult = PaymentGateway.Result.SUCCESS;

            Order order = facade.checkout(new ShopFacade.CheckoutRequest(
                    1L, "u@shop.com", validCard(), validAddress()));

            assertEquals(OrderStatus.PAID, order.getStatus());
            assertEquals(1, stubPayment.calls, "Стаб вызван ровно 1 раз");
            assertEquals(1, email.getSent().size(), "Реальный EmailService отправил письмо");
            assertEquals(9, productRepo.findById(laptop.id()).orElseThrow().stock(),
                    "Реальный репозиторий уменьшил склад");
        }

        @Test
        @DisplayName("H-04: гибрид — отказ банка, проверяем обработку на всех слоях")
        void h04_hybridDeclined() {
            facade.addToCart(1L, laptop.id(), 1);
            stubPayment.nextResult = PaymentGateway.Result.DECLINED;

            assertThrows(IllegalStateException.class,
                    () -> facade.checkout(new ShopFacade.CheckoutRequest(
                            1L, "u@shop.com", validCard(), validAddress())));

            assertEquals(0, email.getSent().size(), "Email не отправлен");
            assertEquals(1, orderRepo.findAll().size());
            assertEquals(OrderStatus.FAILED, orderRepo.findAll().get(0).getStatus());
            assertTrue(log.getEntries().stream().anyMatch(e -> e.result().equals("declined")),
                    "Отказ залогирован");
        }
    }


    @Nested
    @DisplayName("Уровень 3: гибрид — реальный PaymentGateway, стаб EmailService")
    class Level3_HybridEmail {

        ShopFacade facade;
        ProductRepository productRepo;
        OrderRepository orderRepo;
        StubEmailProvider stubEmail;
        LogService log;
        Product laptop;

        @BeforeEach
        void setUp() {
            productRepo = new ProductRepository();
            orderRepo = new OrderRepository();
            stubEmail = new StubEmailProvider();
            log = new LogService();
            facade = new ShopFacade(productRepo, orderRepo,
                    new PaymentGateway(), stubEmail, log);
            laptop = productRepo.save("Ноутбук ASUS", "Электроника", 50000, 10);
        }

        @Test
        @DisplayName("H-05: гибрид — реальный PaymentGateway, стаб email (внешний сервис)")
        void h05_hybridEmailStubbed() {
            facade.addToCart(1L, laptop.id(), 1);

            Order order = facade.checkout(new ShopFacade.CheckoutRequest(
                    1L, "client@shop.com", validCard(), validAddress()));

            assertEquals(OrderStatus.PAID, order.getStatus());
            assertEquals(1, stubEmail.captured.size(), "Стаб email получил 1 письмо");
            assertEquals("client@shop.com", stubEmail.captured.get(0).to());
            assertEquals(9, productRepo.findById(laptop.id()).orElseThrow().stock());
        }

        @Test
        @DisplayName("H-06: decline-карта на реальном gateway, стаб email не вызывается")
        void h06_realDeclineStubEmail() {
            facade.addToCart(1L, laptop.id(), 1);
            PaymentDetails declined = PaymentDetails.card("4000000000000002", "12/30", "123");

            assertThrows(IllegalStateException.class,
                    () -> facade.checkout(new ShopFacade.CheckoutRequest(
                            1L, "u@shop.com", declined, validAddress())));

            assertEquals(0, stubEmail.captured.size(), "Email не вызывается при отказе");
        }
    }


    @Nested
    @DisplayName("Уровень 4: оба внешних сервиса — стабы, вся бизнес-логика реальная")
    class Level4_BothStubbed {

        ShopFacade facade;
        ProductRepository productRepo;
        OrderRepository orderRepo;
        StubPaymentProvider stubPayment;
        StubEmailProvider stubEmail;
        LogService log;
        Product laptop;
        Product phone;

        @BeforeEach
        void setUp() {
            productRepo = new ProductRepository();
            orderRepo = new OrderRepository();
            stubPayment = new StubPaymentProvider();
            stubEmail = new StubEmailProvider();
            log = new LogService();
            facade = new ShopFacade(productRepo, orderRepo,
                    stubPayment, stubEmail, log);
            laptop = productRepo.save("Ноутбук ASUS", "Электроника", 50000, 10);
            phone  = productRepo.save("iPhone 15", "Электроника", 80000, 5);
        }

        @Test
        @DisplayName("H-07: гибрид — несколько заказов подряд, оба стаба, реальные repo и cart")
        void h07_multiOrderHybrid() {
            facade.addToCart(1L, laptop.id(), 2);
            stubPayment.nextResult = PaymentGateway.Result.SUCCESS;
            facade.checkout(new ShopFacade.CheckoutRequest(
                    1L, "u1@shop.com", validCard(), validAddress()));

            facade.addToCart(2L, phone.id(), 1);
            facade.checkout(new ShopFacade.CheckoutRequest(
                    2L, "u2@shop.com", validCard(), validAddress()));

            assertEquals(2, stubPayment.calls, "Стаб оплаты вызван 2 раза");
            assertEquals(2, stubEmail.captured.size(), "Стаб email получил 2 письма");
            assertEquals(2, orderRepo.findAll().size(), "Оба заказа сохранены");
            assertEquals(8, productRepo.findById(laptop.id()).orElseThrow().stock());
            assertEquals(4, productRepo.findById(phone.id()).orElseThrow().stock());
        }

        @Test
        @DisplayName("H-08: гибрид — поиск + корзина + заказ через реальные модули, стаб только для внешних")
        void h08_searchToOrderHybrid() {
            var found = facade.search(new ShopFacade.SearchRequest(
                    "iPhone", "Электроника", null, null,
                    SearchService.SortBy.PRICE_ASC));
            assertEquals(1, found.size());
            assertEquals("iPhone 15", found.get(0).name());

            facade.addToCart(1L, found.get(0).id(), 1);
            assertEquals(80000 * 1.20, facade.getCartTotal(1L), 0.01);

            Order order = facade.checkout(new ShopFacade.CheckoutRequest(
                    1L, "buyer@shop.com", validCard(), validAddress()));

            assertEquals(OrderStatus.PAID, order.getStatus());
            assertEquals(1, stubEmail.captured.size());
        }
    }
}