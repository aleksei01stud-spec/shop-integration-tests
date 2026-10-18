package com.example.shoptests.integration;

import com.example.shoptests.model.Address;
import com.example.shoptests.model.Order;
import com.example.shoptests.model.OrderStatus;
import com.example.shoptests.model.PaymentDetails;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("КТ: Сверху вниз интеграционное тестирование")
class TopDownIntegrationTest {



    static class StubProductRepository extends ProductRepository {
        final Map<Long, Product> canned = new LinkedHashMap<>();
        int decrementCalls = 0;
        boolean allowDecrement = true;

        void seed(Product p) { canned.put(p.id(), p); }

        @Override
        public Optional<Product> findById(long id) {
            return Optional.ofNullable(canned.get(id));
        }

        @Override
        public List<Product> findAll() {
            return new ArrayList<>(canned.values());
        }

        @Override
        public synchronized boolean decrementStock(long productId, int qty) {
            decrementCalls++;
            if (!allowDecrement) return false;
            Product p = canned.get(productId);
            if (p == null || p.stock() < qty) return false;
            canned.put(productId, p.withStock(p.stock() - qty));
            return true;
        }
    }

    static class StubOrderRepository extends OrderRepository {
        final Map<Long, Order> store = new LinkedHashMap<>();
        long seq = 1;

        @Override
        public Order save(Order o) { store.put(o.getId(), o); return o; }

        @Override
        public Optional<Order> findById(long id) { return Optional.ofNullable(store.get(id)); }

        @Override
        public List<Order> findAll() { return new ArrayList<>(store.values()); }

        @Override
        public long nextId() { return seq++; }
    }

    static class StubPaymentGateway extends PaymentGateway {
        Result nextResult = Result.SUCCESS;
        int calls = 0;

        @Override
        public Result process(PaymentDetails details, double amount) {
            calls++;
            return nextResult;
        }
    }

    static class StubEmailService extends EmailService {
        final List<Email> captured = new ArrayList<>();

        @Override
        public void sendOrderConfirmation(String to, Order order) {
            captured.add(new Email(to, "Subject", "Body"));
        }

        @Override
        public List<Email> getSent() { return List.copyOf(captured); }
    }

    static class StubLogService extends LogService {
        final List<LogEntry> captured = new ArrayList<>();

        @Override
        public void log(Level level, String op, long uid, String result) {
            captured.add(new LogEntry(level, op, uid, result, System.currentTimeMillis()));
        }

        @Override
        public List<LogEntry> getEntries() { return List.copyOf(captured); }
    }


    static final Product TEST_PRODUCT = new Product(1L, "Test", "Cat", 1000.0, 10);

    static PaymentDetails validCard() {
        return PaymentDetails.card("4111111111111111", "12/30", "123");
    }

    static Address validAddress() {
        return new Address("Москва", "Тверская 1", "101000");
    }


    @Nested
    @DisplayName("Уровень 1: OrderService с заглушками всех нижних модулей")
    class Level1 {

        StubProductRepository productRepo;
        StubOrderRepository orderRepo;
        StubPaymentGateway payment;
        StubEmailService email;
        StubLogService log;
        CartService cartService;
        OrderService orderService;

        @BeforeEach
        void setUp() {
            productRepo = new StubProductRepository();
            orderRepo = new StubOrderRepository();
            payment = new StubPaymentGateway();
            email = new StubEmailService();
            log = new StubLogService();

            cartService = new CartService(productRepo);
            orderService = new OrderService(cartService, productRepo, orderRepo,
                    payment, email, log);

            productRepo.seed(TEST_PRODUCT);
        }

        @Test
        @DisplayName("L1-01: успешный заказ — проверяем вызовы каждой зависимости")
        void l1_success() {
            cartService.addItem(1L, 1L, 1);

            Order order = orderService.checkout(1L, "u@x.com", validCard(), validAddress());

            assertEquals(OrderStatus.PAID, order.getStatus());
            assertEquals(1, payment.calls, "PaymentGateway должен быть вызван ровно один раз");
            assertEquals(1, email.getSent().size(), "EmailService должен получить один вызов");
            assertEquals(1, productRepo.decrementCalls, "Склад должен уменьшиться один раз");
            assertTrue(log.captured.stream()
                            .anyMatch(e -> e.operation().equals("payment")
                                    && e.result().equals("success")),
                    "В логе должна быть запись об успешной оплате. Лог: " + log.captured);
        }

        @Test
        @DisplayName("L1-02: отказ банка — верхний модуль корректно обрабатывает ошибку")
        void l1_decline() {
            cartService.addItem(1L, 1L, 1);
            payment.nextResult = PaymentGateway.Result.DECLINED;

            assertThrows(IllegalStateException.class,
                    () -> orderService.checkout(1L, "u@x.com", validCard(), validAddress()));

            assertEquals(0, email.getSent().size(), "Email НЕ должен отправляться при отказе");
            assertTrue(log.captured.stream()
                            .anyMatch(e -> e.result().equals("declined")),
                    "В логе должна быть запись declined. Лог: " + log.captured);
        }

        @Test
        @DisplayName("L1-03: изоляция верхнего модуля — стубы позволяют управлять поведением")
        void l1_isolation() {
            cartService.addItem(1L, 1L, 1);
            productRepo.allowDecrement = false;

            assertThrows(IllegalStateException.class,
                    () -> orderService.checkout(1L, "u@x.com", validCard(), validAddress()),
                    "Ожидаем ошибку 'out of stock'");

            assertEquals(0, email.getSent().size(), "Email не отправляется");
            assertEquals(0, orderRepo.findAll().size(),
                    "Заказ не должен сохраниться, т.к. списание со склада провалилось. " +
                            "Найдено заказов: " + orderRepo.findAll().size());

            assertTrue(log.captured.stream()
                            .anyMatch(e -> e.result().startsWith("out_of_stock")),
                    "В логе должна быть запись out_of_stock. Лог: " + log.captured);
        }
    }


    @Nested
    @DisplayName("Уровень 2: + реальные CartService и ProductRepository")
    class Level2 {

        ProductRepository productRepo;
        CartService cartService;
        StubOrderRepository orderRepo;
        StubPaymentGateway payment;
        StubEmailService email;
        StubLogService log;
        OrderService orderService;

        Product laptop;

        @BeforeEach
        void setUp() {
            productRepo = new ProductRepository();
            cartService = new CartService(productRepo);
            orderRepo = new StubOrderRepository();
            payment = new StubPaymentGateway();
            email = new StubEmailService();
            log = new StubLogService();

            orderService = new OrderService(cartService, productRepo, orderRepo,
                    payment, email, log);

            laptop = productRepo.save("Ноутбук ASUS", "Электроника", 50000, 10);
        }

        @Test
        @DisplayName("L2-01: корзина считает сумму, склад реально уменьшается")
        void l2_realCartAndStock() {
            cartService.addItem(1L, laptop.id(), 2);

            Order order = orderService.checkout(1L, "u@x.com", validCard(), validAddress());

            assertEquals(OrderStatus.PAID, order.getStatus());
            assertEquals(100000 * 1.20, order.getTotal(), 0.01);
            assertEquals(8, productRepo.findById(laptop.id()).orElseThrow().stock(),
                    "Склад должен реально уменьшиться с 10 до 8");
        }

        @Test
        @DisplayName("L2-02: подмена репозитория заглушкой ранее маскировала баг склада")
        void l2_stockOutAfterTwoOrders() {
            cartService.addItem(1L, laptop.id(), 5);
            orderService.checkout(1L, "u1@x.com", validCard(), validAddress());

            cartService.addItem(2L, laptop.id(), 5);
            orderService.checkout(2L, "u2@x.com", validCard(), validAddress());

            assertEquals(0, productRepo.findById(laptop.id()).orElseThrow().stock());
        }
    }


    @Nested
    @DisplayName("Уровень 3: + реальные EmailService и LogService")
    class Level3 {

        ProductRepository productRepo;
        CartService cartService;
        StubOrderRepository orderRepo;
        StubPaymentGateway payment;
        EmailService email;
        LogService log;
        OrderService orderService;

        Product laptop;

        @BeforeEach
        void setUp() {
            productRepo = new ProductRepository();
            cartService = new CartService(productRepo);
            orderRepo = new StubOrderRepository();
            payment = new StubPaymentGateway();
            email = new EmailService();
            log = new LogService();

            orderService = new OrderService(cartService, productRepo, orderRepo,
                    payment, email, log);

            laptop = productRepo.save("Ноутбук ASUS", "Электроника", 50000, 10);
        }

        @Test
        @DisplayName("L3-01: email реально формируется с деталями заказа")
        void l3_emailContent() {
            cartService.addItem(1L, laptop.id(), 1);
            Order order = orderService.checkout(1L, "client@shop.com", validCard(), validAddress());

            assertEquals(1, email.getSent().size());
            assertEquals("client@shop.com", email.getSent().get(0).to());

            assertTrue(email.getSent().get(0).subject().contains("#" + order.getId()),
                    "Тема должна содержать номер заказа '#" + order.getId() + "'. " +
                            "Фактическая тема: " + email.getSent().get(0).subject());

            assertTrue(email.getSent().get(0).body().contains("items=1"),
                    "Тело письма должно содержать 'items=1'. " +
                            "Фактическое тело: " + email.getSent().get(0).body());
        }

        @Test
        @DisplayName("L3-02: интеграция с LogService не сломала верхний уровень")
        void l3_logging() {
            cartService.addItem(1L, laptop.id(), 1);
            orderService.checkout(1L, "u@x.com", validCard(), validAddress());

            var entries = log.getEntries();
            assertTrue(entries.stream().anyMatch(e -> e.operation().equals("payment")),
                    "Нет записи о payment. Лог: " + entries);
            assertTrue(entries.stream().anyMatch(e -> e.operation().equals("email")),
                    "Нет записи о email. Лог: " + entries);
            assertTrue(entries.stream().anyMatch(e -> e.operation().equals("checkout")),
                    "Нет записи о checkout. Лог: " + entries);
        }
    }


    @Nested
    @DisplayName("Уровень 4: полная интеграция (все модули реальные)")
    class Level4 {

        ProductRepository productRepo;
        OrderRepository orderRepo;
        CartService cartService;
        PaymentGateway payment;
        EmailService email;
        LogService log;
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
        @DisplayName("L4-01: полная цепочка — всё работает вместе")
        void l4_fullIntegration() {
            cartService.addItem(1L, laptop.id(), 2);

            Order order = orderService.checkout(1L, "u@x.com", validCard(), validAddress());

            assertEquals(OrderStatus.PAID, order.getStatus());
            assertEquals(8, productRepo.findById(laptop.id()).orElseThrow().stock());
            assertEquals(1, email.getSent().size());
            assertEquals(1, orderRepo.findAll().size());
            assertTrue(log.getEntries().stream().anyMatch(e -> e.result().equals("success")),
                    "Нет успешной записи в логе. Лог: " + log.getEntries());
        }

        @Test
        @DisplayName("L4-02: реальный PaymentGateway корректно отклоняет decline-карту")
        void l4_declineFromRealGateway() {
            cartService.addItem(1L, laptop.id(), 1);
            PaymentDetails declinedCard = PaymentDetails.card("4000000000000002", "12/30", "123");

            assertThrows(IllegalStateException.class,
                    () -> orderService.checkout(1L, "u@x.com", declinedCard, validAddress()));

            assertEquals(0, email.getSent().size());
            assertTrue(log.getEntries().stream().anyMatch(e -> e.result().equals("declined")),
                    "Нет declined в логе. Лог: " + log.getEntries());
        }

        @Test
        @DisplayName("L4-03: невалидная карта — реальный PaymentGateway вернёт INVALID_DATA")
        void l4_invalidCard() {
            cartService.addItem(1L, laptop.id(), 1);
            PaymentDetails bad = PaymentDetails.card("1234-5678", "01/20", "9");

            assertThrows(IllegalArgumentException.class,
                    () -> orderService.checkout(1L, "u@x.com", bad, validAddress()),
                    "Ожидаем IllegalArgumentException за невалидные данные карты");

            assertEquals(0, email.getSent().size());
        }
    }
}