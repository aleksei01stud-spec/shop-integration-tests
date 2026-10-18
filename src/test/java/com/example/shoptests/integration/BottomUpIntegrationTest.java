package com.example.shoptests.integration;

import com.example.shoptests.model.Address;
import com.example.shoptests.model.Order;
import com.example.shoptests.model.OrderStatus;
import com.example.shoptests.model.PaymentDetails;
import com.example.shoptests.model.PaymentMethod;
import com.example.shoptests.model.Product;
import com.example.shoptests.repository.OrderRepository;
import com.example.shoptests.repository.ProductRepository;
import com.example.shoptests.service.CartService;
import com.example.shoptests.service.EmailService;
import com.example.shoptests.service.LogService;
import com.example.shoptests.service.OrderService;
import com.example.shoptests.service.PaymentGateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("КТ: Снизу вверх интеграционное тестирование")
class BottomUpIntegrationTest {

    static Address validAddress() {
        return new Address("Москва", "Тверская 1", "101000");
    }

    static PaymentDetails validCard() {
        return PaymentDetails.card("4111111111111111", "12/30", "123");
    }


    @Nested
    @DisplayName("Уровень 1: базовые репозитории")
    class Level1 {

        ProductRepository productRepo;
        OrderRepository orderRepo;

        @BeforeEach
        void setUp() {
            productRepo = new ProductRepository();
            orderRepo = new OrderRepository();
        }

        @Test
        @DisplayName("L1-01: ProductRepository — сохранение и поиск товара")
        void l1_productRepoCrud() {
            Product p = productRepo.save("Ноутбук ASUS", "Электроника", 50000, 10);
            assertTrue(p.id() > 0, "ID должен быть присвоен");

            Optional<Product> found = productRepo.findById(p.id());
            assertTrue(found.isPresent(), "Товар должен находиться по ID");
            assertEquals("Ноутбук ASUS", found.get().name());

            assertEquals(1, productRepo.findAll().size());
        }

        @Test
        @DisplayName("L1-02: ProductRepository — атомарное уменьшение склада")
        void l1_stockDecrement() {
            Product p = productRepo.save("Test", "Cat", 1000, 10);

            assertTrue(productRepo.decrementStock(p.id(), 3), "Списание 3 из 10 должно пройти");
            assertEquals(7, productRepo.findById(p.id()).orElseThrow().stock());

            assertTrue(productRepo.decrementStock(p.id(), 7), "Списание 7 из 7 должно пройти");
            assertEquals(0, productRepo.findById(p.id()).orElseThrow().stock());

            assertFalse(productRepo.decrementStock(p.id(), 1), "Списание из пустого склада — false");
        }

        @Test
        @DisplayName("L1-03: OrderRepository — сохранение и поиск заказа")
        void l1_orderRepoCrud() {
            long id = orderRepo.nextId();
            Order o = new Order(id, 1L, List.of(), 1234.5,
                    PaymentMethod.CREDIT_CARD, validAddress());
            orderRepo.save(o);

            assertTrue(orderRepo.findById(id).isPresent());
            assertEquals(1, orderRepo.findAll().size());
            assertEquals(OrderStatus.PENDING, orderRepo.findById(id).orElseThrow().getStatus());
        }
    }


    @Nested
    @DisplayName("Уровень 2: инфраструктурные сервисы (PaymentGateway, EmailService, LogService)")
    class Level2 {

        PaymentGateway gateway;
        EmailService email;
        LogService log;

        @BeforeEach
        void setUp() {
            gateway = new PaymentGateway();
            email = new EmailService();
            log = new LogService();
        }

        @Test
        @DisplayName("L2-01: PaymentGateway — успех, отказ, невалидные данные")
        void l2_paymentGateway() {
            assertEquals(PaymentGateway.Result.SUCCESS,
                    gateway.process(validCard(), 1000.0));

            PaymentDetails declined = PaymentDetails.card("4000000000000002", "12/30", "123");
            assertEquals(PaymentGateway.Result.DECLINED,
                    gateway.process(declined, 1000.0));

            PaymentDetails bad = PaymentDetails.card("1234", "01/20", "9");
            assertEquals(PaymentGateway.Result.INVALID_DATA,
                    gateway.process(bad, 1000.0));
        }

        @Test
        @DisplayName("L2-02: EmailService — формирование письма с деталями заказа")
        void l2_emailService() {
            Order o = new Order(42L, 1L, List.of(), 1234.5,
                    PaymentMethod.CREDIT_CARD, validAddress());
            email.sendOrderConfirmation("client@shop.com", o);

            assertEquals(1, email.getSent().size());
            EmailService.Email mail = email.getSent().get(0);
            assertEquals("client@shop.com", mail.to());
            assertTrue(mail.subject().contains("#42"),
                    "Тема должна содержать номер заказа: " + mail.subject());
        }

        @Test
        @DisplayName("L2-03: LogService — сохранение записей с разными уровнями")
        void l2_logService() {
            log.log(LogService.Level.INFO, "payment", 1L, "success");
            log.log(LogService.Level.WARN, "payment", 2L, "declined");
            log.log(LogService.Level.ERROR, "checkout", 2L, "out_of_stock");

            assertEquals(3, log.getEntries().size());
            assertTrue(log.getEntries().stream()
                    .anyMatch(e -> e.level() == LogService.Level.WARN));
            assertTrue(log.getEntries().stream()
                    .anyMatch(e -> e.level() == LogService.Level.ERROR));
        }
    }


    @Nested
    @DisplayName("Уровень 3: CartService + ProductRepository")
    class Level3 {

        ProductRepository productRepo;
        CartService cartService;
        Product laptop;
        Product phone;

        @BeforeEach
        void setUp() {
            productRepo = new ProductRepository();
            cartService = new CartService(productRepo);
            laptop = productRepo.save("Ноутбук ASUS", "Электроника", 50000, 10);
            phone  = productRepo.save("iPhone 15",   "Электроника", 80000, 5);
        }

        @Test
        @DisplayName("L3-01: добавление, изменение количества, удаление, расчёт суммы")
        void l3_cartOperations() {
            cartService.addItem(1L, laptop.id(), 1);
            cartService.addItem(1L, phone.id(), 2);
            assertEquals(2, cartService.getOrCreate(1L).getItems().size());

            cartService.updateQuantity(1L, phone.id(), 5);
            cartService.removeItem(1L, laptop.id());

            assertEquals(480000.0, cartService.calculateTotal(1L), 0.01);
        }

        @Test
        @DisplayName("L3-02: промокод SALE10 уменьшает итог на 10%")
        void l3_discount() {
            cartService.addItem(1L, laptop.id(), 1);
            assertEquals(50000 * 1.20, cartService.calculateTotal(1L), 0.01);

            cartService.applyPromoCode(1L, "SALE10");
            assertEquals(50000 * 0.9 * 1.20, cartService.calculateTotal(1L), 0.01);
        }

        @Test
        @DisplayName("L3-03: CartService видит реальный склад через ProductRepository")
        void l3_cartSeesStock() {
            Product limited = productRepo.save("Limited", "Электроника", 1000, 2);
            cartService.addItem(1L, limited.id(), 5);

            assertEquals(2, productRepo.findById(limited.id()).orElseThrow().stock(),
                    "Склад не должен меняться при добавлении в корзину");
        }
    }


    @Nested
    @DisplayName("Уровень 4: OrderService + все нижние модули")
    class Level4 {

        ProductRepository productRepo;
        OrderRepository orderRepo;
        PaymentGateway payment;
        EmailService email;
        LogService log;
        CartService cartService;
        OrderService orderService;
        Product laptop;

        @BeforeEach
        void setUp() {
            productRepo = new ProductRepository();
            orderRepo = new OrderRepository();
            payment = new PaymentGateway();
            email = new EmailService();
            log = new LogService();

            cartService = new CartService(productRepo);
            orderService = new OrderService(cartService, productRepo, orderRepo,
                    payment, email, log);

            laptop = productRepo.save("Ноутбук ASUS", "Электроника", 50000, 10);
        }

        @Test
        @DisplayName("L4-01: полный сквозной заказ с картой")
        void l4_fullCheckout() {
            cartService.addItem(1L, laptop.id(), 2);

            Order order = orderService.checkout(1L, "u@x.com", validCard(), validAddress());

            assertEquals(OrderStatus.PAID, order.getStatus());
            assertEquals(100000 * 1.20, order.getTotal(), 0.01);
            assertEquals(8, productRepo.findById(laptop.id()).orElseThrow().stock());
            assertEquals(1, email.getSent().size());
            assertEquals(1, orderRepo.findAll().size());
            assertTrue(log.getEntries().stream().anyMatch(e -> e.result().equals("success")));
        }

        @Test
        @DisplayName("L4-02: отказ банка — заказ в статусе FAILED")
        void l4_bankDecline() {
            cartService.addItem(1L, laptop.id(), 1);
            PaymentDetails declined = PaymentDetails.card("4000000000000002", "12/30", "123");

            assertThrows(IllegalStateException.class,
                    () -> orderService.checkout(1L, "u@x.com", declined, validAddress()));

            assertEquals(0, email.getSent().size(), "Email не отправляется при отказе");
            assertEquals(1, orderRepo.findAll().size());
            assertEquals(OrderStatus.FAILED, orderRepo.findAll().get(0).getStatus());
            assertTrue(log.getEntries().stream().anyMatch(e -> e.result().equals("declined")));
        }

        @Test
        @DisplayName("L4-03: товар закончился — заказ не создан")
        void l4_outOfStock() {
            Product limited = productRepo.save("Limited", "Электроника", 1000, 1);
            cartService.addItem(1L, limited.id(), 2);

            assertThrows(IllegalStateException.class,
                    () -> orderService.checkout(1L, "u@x.com", validCard(), validAddress()));

            assertEquals(0, orderRepo.findAll().size(), "Заказ не сохранён");
            assertEquals(0, email.getSent().size());
        }

        @Test
        @DisplayName("L4-04: невалидный адрес — заказ не создан")
        void l4_invalidAddress() {
            cartService.addItem(1L, laptop.id(), 1);
            Address bad = new Address("", "A", "000");

            assertThrows(IllegalArgumentException.class,
                    () -> orderService.checkout(1L, "u@x.com", validCard(), bad));

            assertEquals(0, email.getSent().size());
        }

        @Test
        @DisplayName("L4-05: полный лог — все операции записаны")
        void l4_fullLogging() {
            cartService.addItem(1L, laptop.id(), 1);
            orderService.checkout(1L, "u@x.com", validCard(), validAddress());

            var entries = log.getEntries();
            assertTrue(entries.stream().anyMatch(e -> e.operation().equals("payment")),
                    "Нет записи payment. Лог: " + entries);
            assertTrue(entries.stream().anyMatch(e -> e.operation().equals("email")),
                    "Нет записи email. Лог: " + entries);
            assertTrue(entries.stream().anyMatch(e -> e.operation().equals("checkout")),
                    "Нет записи checkout. Лог: " + entries);
        }
    }
}