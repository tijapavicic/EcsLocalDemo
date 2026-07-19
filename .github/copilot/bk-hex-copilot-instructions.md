# Copilot Repository Instructions

**Project Type:** Java/Spring Boot + TypeScript/Web Components Hexagonal Marketplace  
**Architecture:** Hexagonal (Ports & Adapters)  
**Business Model:** Multi-seller marketplace with 5% commission per transaction

---

## Code and architecture conventions
- Keep Java code under `com.example.user`.
- Respect module boundaries: `hex-core`, `hex-payment-core`, `hex-inbound-adapter-web`, `hex-inbound-adapter-payment-web`, `hex-outbound-adapter-db`, `hex-outbound-adapter-payment-db`, and `hex-application`.
- Keep controllers in `api`, config in `config`, and domain models in `model`.
- Keep adapters hexagonal: inbound adapters call `port.in`; outbound adapters implement `port.out`.
- Do not manually edit generated output under `target/`; edit source files only.
- Prefer small, focused PRs and minimal diffs.

---

## SOLID Principles for Hexagonal Architecture

Apply SOLID principles to maintain clean, maintainable, decoupled code. These principles reinforce hexagonal architecture's core goal: isolate business logic from external dependencies.

### **S — Single Responsibility Principle (SRP)**

**Definition:** A class should have only one reason to change.

**Hexagonal Application:**
- Domain models focus on business logic only (Order, Product, Transaction, Commission)
- Adapters handle one integration concern (DBRepository, RestController, PaymentGateway)
- Ports define single contracts (CreateOrderPort, ProcessPaymentPort, RecordTransactionPort)

**Anti-Pattern (Violation):**
```java
// ❌ DO NOT: God class mixing domain logic + DB + REST concerns
public class OrderService {
    public void createOrderAndSaveAndRespond(CreateOrderRequest req) {
        // validate request
        // calculate totals
        // save to DB
        // call payment API
        // format JSON response
    }
}
```

**Pattern (Compliant):**
```java
// ✅ DO: Separate concerns into focused classes

// Domain logic only
public class CreateOrderUseCase {
    private final OrderRepository repository;
    private final CommissionCalculator calculator;
    
    public Order execute(CreateOrderCommand cmd) {
        // Business logic: validate, calculate, create order
        Order order = Order.create(cmd.getBuyerId(), cmd.getItems());
        BigDecimal commission = calculator.calculateCommission(order.getGrossAmount());
        order.recordCommission(commission);
        return repository.save(order);
    }
}

// REST adapter only handles HTTP concerns
@RestController
@RequestMapping("/api/v1/orders")
public class OrderControllerAdapter {
    private final CreateOrderPort port;
    
    @PostMapping
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order order = port.execute(new CreateOrderCommand(request.getBuyerId(), request.getItems()));
        return OrderResponse.fromDomain(order);
    }
}
```

**Marketplace SRP Checklist:**
- ✅ Domain models (`Order`, `Transaction`, `Seller`) contain business rules only
- ✅ Adapters (`OrderRepositoryAdapter`, `OrderControllerAdapter`) handle one external concern
- ✅ Ports define single use-case per interface
- ✅ Exception types are specific to domain concern (e.g., `InsufficientStockException`, not generic `Exception`)

---

### **O — Open/Closed Principle (OCP)**

**Definition:** Classes should be open for extension, closed for modification.

**Hexagonal Application:**
- Ports (interfaces) define contracts; adapters implement without changing ports
- New payment methods extend `PaymentProcessor` without modifying existing code
- New seller review sources extend `ReviewRepository` without touching order logic

**Anti-Pattern (Violation):**
```java
// ❌ DO NOT: Modify existing class every time you add a new payment method
public class PaymentProcessor {
    public boolean processPayment(Order order, String method) {
        if ("stripe".equals(method)) {
            // stripe logic
        } else if ("paypal".equals(method)) {
            // paypal logic
        } else if ("klarna".equals(method)) {
            // must add new else-if here — VIOLATION
            // modifying existing class
        }
    }
}
```

**Pattern (Compliant):**
```java
// ✅ DO: Define port, implement for each payment method

// Port (closed for modification)
public interface PaymentGateway {
    PaymentResult processPayment(PaymentRequest request);
}

// Implementations (open for extension)
public class StripePaymentAdapter implements PaymentGateway {
    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        // Stripe-specific logic
    }
}

public class PayPalPaymentAdapter implements PaymentGateway {
    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        // PayPal-specific logic
    }
}

// Spring config (add new adapter without changing processor)
@Configuration
public class PaymentConfig {
    @Bean
    @Qualifier("stripe")
    public PaymentGateway stripePayment() { return new StripePaymentAdapter(); }
    
    @Bean
    @Qualifier("paypal")
    public PaymentGateway paypalPayment() { return new PayPalPaymentAdapter(); }
}
```

**Marketplace OCP Checklist:**
- ✅ Payment methods extensible via `PaymentGateway` interface
- ✅ Notification channels extensible via `NotificationPort` interface
- ✅ Commission calculation rules extensible via `CommissionStrategy` strategy pattern
- ✅ Repository adapters implement `OrderRepository`, `ProductRepository` without changing use-case logic
- ✅ New seller payout methods don't require changes to `PayoutService`

---

### **L — Liskov Substitution Principle (LSP)**

**Definition:** Subtypes must be substitutable for their supertypes without breaking behavior.

**Hexagonal Application:**
- All `PaymentGateway` implementations must behave identically from caller's perspective
- All `OrderRepository` implementations must return consistent data
- All port implementations must honor the contract or throw documented exceptions

**Anti-Pattern (Violation):**
```java
// ❌ DO NOT: Subtype that violates the contract
public class MockPaymentAdapter implements PaymentGateway {
    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        // Returning hardcoded success instead of actually validating
        // Real code expects this to validate card, raise exceptions for declined cards
        // BUT mock silently succeeds — VIOLATION: violates contract assumptions
        return PaymentResult.success("mock-123");
    }
}

// Calling code assumes:
// - Real adapter: validateCard(), may throw InvalidCardException
// - Mock adapter: assumed same behavior in tests, but doesn't — breaks test validity
```

**Pattern (Compliant):**
```java
// ✅ DO: Subtype honors contract fully

public class MockPaymentAdapter implements PaymentGateway {
    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        // If card is invalid, throw same exception as real adapter
        if (!isValidCard(request.getCardNumber())) {
            throw new InvalidCardException("Card number invalid");
        }
        // If request is valid, return success (same behavior as real adapter)
        return PaymentResult.success("mock-" + UUID.randomUUID());
    }
}

// Now mock is substitutable for real adapter in tests
// Both enforce the same contract
```

**Marketplace LSP Checklist:**
- ✅ All `PaymentGateway` implementations handle declined payments consistently
- ✅ All `OrderRepository` implementations return `Order` with consistent state
- ✅ All `NotificationPort` implementations don't silently fail; they throw documented exceptions
- ✅ Test doubles (mocks) honor the same contract as production implementations
- ✅ Commission calculation implementations always return `BigDecimal` with consistent precision

---

### **I — Interface Segregation Principle (ISP)**

**Definition:** Clients should not be forced to depend on interfaces they don't use.

**Hexagonal Application:**
- Port interfaces are narrow and focused (one method or coherent group)
- Adapters implement only the ports they need
- Clients depend on minimal, focused interfaces

**Anti-Pattern (Violation):**
```java
// ❌ DO NOT: Fat interface that forces implementations to do too much
public interface OrderPort {
    Order createOrder(CreateOrderCommand cmd);
    Order getOrder(Long id);
    void updateOrder(Order order);
    void deleteOrder(Long id);
    List<Order> listAllOrders();
    void emailBuyer(Long orderId);  // ← Not all clients want this
    void updateInventory(Long orderId);  // ← Not all clients want this
    void processRefund(Long orderId);  // ← Not all clients want this
}

// Client only needs to create orders, but forced to implement all methods
public class MockOrderPort implements OrderPort {
    public Order createOrder(CreateOrderCommand cmd) { ... }
    public Order getOrder(Long id) { throw new UnsupportedOperationException(); }
    public void updateOrder(Order order) { throw new UnsupportedOperationException(); }
    // etc., lots of throw new UnsupportedOperationException()
}
```

**Pattern (Compliant):**
```java
// ✅ DO: Segregate into focused interfaces

public interface CreateOrderPort {
    Order execute(CreateOrderCommand cmd);
}

public interface GetOrderPort {
    Optional<Order> getById(Long id);
}

public interface UpdateOrderPort {
    Order execute(UpdateOrderCommand cmd);
}

public interface NotifyBuyerPort {
    void sendOrderConfirmation(Long orderId);
}

// Client only depends on what it needs
public class OrderControllerAdapter {
    private final CreateOrderPort createPort;
    private final GetOrderPort getPort;
    
    // Only needs CreateOrderPort and GetOrderPort
    // Not forced to know about NotifyBuyerPort
}

// Test implementation minimal and focused
public class MockCreateOrderPort implements CreateOrderPort {
    public Order execute(CreateOrderCommand cmd) { ... }
    // That's it; no extraneous methods to stub
}
```

**Marketplace ISP Checklist:**
- ✅ Ports are narrow: `CreateOrderPort`, `GetOrderPort`, `UpdateOrderPort` (not one `OrderPort`)
- ✅ `ProcessPaymentPort` focused on payment only; not coupled to inventory/shipping
- ✅ `RecordTransactionPort` handles commission split; not responsible for refunds
- ✅ Seller dashboard components depend only on `GetEarningsPort`, not full seller operations
- ✅ Product search doesn't require `ProductUpdatePort`; depends only on `SearchProductsPort`

---

### **D — Dependency Inversion Principle (DIP)**

**Definition:** High-level modules should not depend on low-level modules; both should depend on abstractions.

**Hexagonal Application:**
- Use-cases depend on port abstractions, not adapters
- Adapters injected via Spring `@Autowired` into use-cases
- Domain models remain independent of external dependencies

**Anti-Pattern (Violation):**
```java
// ❌ DO NOT: High-level logic depends on low-level DB/HTTP details
public class CreateOrderUseCase {
    private OrderJPARepository jpaRepo;  // ← Direct dependency on JPA
    private RestTemplate restTemplate;  // ← Direct dependency on Spring REST
    private StripeClient stripeClient;  // ← Direct dependency on Stripe
    
    public Order execute(CreateOrderCommand cmd) {
        // High-level logic mixed with Stripe, JPA, Spring specifics
        StripeToken token = stripeClient.tokenize(cmd.getCard());
        StripeCharge charge = stripeClient.charge(cmd.getPrice(), token);
        OrderDTO dto = new OrderDTO(cmd.getBuyerId(), cmd.getItems());
        jpaRepo.save(dto);
        // Impossible to test without Stripe, JPA, Spring
    }
}
```

**Pattern (Compliant):**
```java
// ✅ DO: Both high-level and low-level depend on abstractions (ports)

// High-level business logic depends only on port abstractions
public class CreateOrderUseCase {
    private final OrderRepository repository;  // ← Port abstraction
    private final PaymentGateway paymentGateway;  // ← Port abstraction
    private final CommissionCalculator calculator;  // ← Port abstraction
    
    public CreateOrderUseCase(
        OrderRepository repository,
        PaymentGateway paymentGateway,
        CommissionCalculator calculator
    ) {
        this.repository = repository;
        this.paymentGateway = paymentGateway;
        this.calculator = calculator;
    }
    
    public Order execute(CreateOrderCommand cmd) {
        // Pure business logic, no external dependencies leaked in
        Order order = Order.create(cmd.getBuyerId(), cmd.getItems());
        BigDecimal commission = calculator.calculateCommission(order.getGrossAmount());
        order.recordCommission(commission);
        return repository.save(order);
    }
}

// Low-level adapters implement the same port abstractions
public class StripePaymentAdapter implements PaymentGateway {
    private final StripeClient stripeClient;
    
    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        // Stripe-specific details isolated here
    }
}

public class PostgresOrderRepositoryAdapter implements OrderRepository {
    private final OrderJPARepository jpaRepo;
    
    @Override
    public Order save(Order order) {
        // JPA-specific details isolated here
    }
}

// Spring config wires abstractions (DIP applied)
@Configuration
public class OrderConfig {
    @Bean
    public CreateOrderUseCase createOrderUseCase(
        OrderRepository repository,
        PaymentGateway paymentGateway,
        CommissionCalculator calculator
    ) {
        return new CreateOrderUseCase(repository, paymentGateway, calculator);
    }
    
    // Easy to swap implementations without changing use-case
    @Bean
    public OrderRepository orderRepository(OrderJPARepository jpaRepo) {
        return new PostgresOrderRepositoryAdapter(jpaRepo);
    }
    
    @Bean
    public PaymentGateway paymentGateway() {
        return new StripePaymentAdapter(stripeClient());
    }
}
```

**Testing is now trivial:**
```java
public class CreateOrderUseCaseTest {
    private CreateOrderUseCase useCase;
    private OrderRepository mockRepository;
    private PaymentGateway mockPaymentGateway;
    
    @BeforeEach
    public void setUp() {
        mockRepository = mock(OrderRepository.class);
        mockPaymentGateway = mock(PaymentGateway.class);
        useCase = new CreateOrderUseCase(mockRepository, mockPaymentGateway, new CommissionCalculator());
    }
    
    @Test
    public void testCreateOrder() {
        // No Stripe SDK, no JPA, no Spring required — pure business logic test
        when(mockPaymentGateway.processPayment(any())).thenReturn(PaymentResult.success("123"));
        when(mockRepository.save(any())).thenReturn(expectedOrder);
        
        Order result = useCase.execute(createOrderCommand);
        
        assertEquals(expectedOrder, result);
    }
}
```

**Marketplace DIP Checklist:**
- ✅ Use-cases depend on `OrderRepository`, `PaymentGateway`, `TransactionRecorder` (ports), not `OrderJPARepository`
- ✅ Controllers depend on ports (e.g., `CreateOrderPort`), not use-case implementation classes
- ✅ Domain models (`Order`, `Product`, `Transaction`) have zero dependency on Spring, Hibernate, or external libraries
- ✅ Configuration class (`OrderConfig`, `PaymentConfig`) is the only place Spring wiring/adapter selection happens
- ✅ All adapters can be swapped (PostgreSQL ↔ MongoDB, Stripe ↔ PayPal) without changing business logic

---

### **SOLID + Hexagonal = Decoupled Architecture**

| Principle | Hexagonal Benefit | Marketplace Example |
|-----------|-------------------|---------------------|
| **SRP** | Each class has one domain responsibility | `CommissionCalculator` does only commission math; `OrderRepository` does only persistence |
| **OCP** | New adapters extend without changing existing code | Add PayPal payment without touching Stripe logic |
| **LSP** | Implementations are substitutable; tests use mocks reliably | Mock `OrderRepository` behaves like real DB in tests |
| **ISP** | Reduce coupling via narrow interfaces | `GetEarningsPort` separate from `RequestPayoutPort` |
| **DIP** | Business logic isolated from infrastructure | `CreateOrderUseCase` knows nothing about PostgreSQL or Stripe |

**Result:** Clean, testable, maintainable codebase where:
- ✅ Ports act as boundaries between concerns
- ✅ Adapters are pluggable implementations (ports)
- ✅ Domain models are framework-agnostic
- ✅ Tests are fast and isolated
- ✅ New features don't require modifying existing code; just add new adapters

---

## Marketplace Domain Knowledge

Read details in `marketplace.md` before implementing any marketplace feature.

### **Core Concepts**
- **Seller**: User with `is_seller=true`, has products, receives payouts
- **Buyer**: User purchasing from sellers
- **Commission**: 5% automatic split (platform = 5%, seller = 95%)
- **Order Lifecycle**: PENDING_PAYMENT → PAID → SHIPPED → DELIVERED → COMPLETED
- **Transaction**: Financial record (buyer, seller, product, amounts, commission)
- **Payout**: Withdrawal request from seller to bank/payment processor

### **Backend Patterns**

**Commission Calculation:**
```java
// Use this pattern in hex-payment-core
public class CommissionCalculation {
    public static final BigDecimal PLATFORM_COMMISSION_RATE = new BigDecimal("0.05"); // 5%
    public static final BigDecimal SELLER_PAYOUT_RATE = new BigDecimal("0.95");     // 95%
    
    public static BigDecimal calculateCommission(BigDecimal grossAmount) {
        return grossAmount.multiply(PLATFORM_COMMISSION_RATE);
    }
    
    public static BigDecimal calculateSellerPayout(BigDecimal grossAmount) {
        return grossAmount.multiply(SELLER_PAYOUT_RATE);
    }
}
```

**Seller Authorization Pattern:**
```java
// For operations on seller products/orders:
// - Validate @AuthenticationPrincipal user.isAdmin() || user.getSellerId().equals(resourceSellerId)
// - Use @PreAuthorize("hasRole('SELLER')") on seller endpoints
// - Log seller_id and operation in MDC for Splunk tracing
```

**Product Ownership Validation:**
```java
// When updating/deleting products:
// - Confirm product.seller_id = authenticated_user.id
// - Return 404 (not 403) if seller doesn't own product (security best practice)
// - Validate is_active status before showing to buyers
```

**Order Access Pattern:**
```java
// Buyers see only their own orders
// Sellers see orders where seller_id = their id
// Use query filters to enforce at database level, not application
```

### **Frontend Patterns**

**Seller Role Detection:**
```typescript
// Check user roles from Keycloak JWT
const isSeller = user?.roles?.includes('seller');

// Conditionally render seller dashboard link
if (isSeller) {
  // Show: Seller Dashboard, My Earnings, My Products
} else {
  // Show: Become a Seller button dialog
}
```

**Cart & Checkout Flow:**
```typescript
// 1. Add to cart (localStorage initially)
// 2. Proceed to checkout (create order + payment)
// 3. After payment success: record transaction with commission split
// 4. Show order confirmation with seller breakdown
```

**Seller Dashboard Components:**
```typescript
// Follow pattern from hex-inbound-adapter-web components
// - Metric cards with actual data (not mocks)
// - Tables with API pagination
// - Forms with validated input
// - Structured logging on all operations
```

## Build and quality gates
- Run before proposing changes:
  - `mvn -B clean verify`
- If dependencies changed, also run:
  - `mvn -B org.owasp:dependency-check-maven:check`
- Treat HIGH and CRITICAL findings as release blockers.

## Security expectations
- Do not hardcode credentials, tokens, or secrets.
- Keep actuator exposure minimal and explicit.
- Validate request payloads and avoid permissive CORS defaults.
- Prefer dependency upgrades that are patch/minor unless otherwise required.

## Output expectations for PR assistance
When proposing security/dependency changes, include:
- impacted dependency and version
- CVE and severity
- minimum fixed version
- brief risk/compatibility notes

## Quality Gates

- Always run before handoff:
  - `mvn -B clean verify`
- If dependencies changed, also run:
  - `mvn -B org.owasp:dependency-check-maven:check`
- Treat HIGH and CRITICAL findings as release blockers.

## Workflow

1. Gather context
  - Read affected code, tests, and configs first.
  - Trace data flow and module boundaries.
2. Plan
  - Propose a minimal implementation and edge cases.
  - Call out assumptions explicitly.
3. Implement
  - Follow existing style and conventions.
  - Handle failures explicitly with typed exceptions and consistent responses.
4. Verify
  - Add or update tests (happy path + at least one edge/failure case).
  - Run module-level tests while iterating, then repository quality gates.
5. Deliver
  - Summarize changed files, rationale, verification commands, and any remaining risks.

## API and Contract Guidance

- Use explicit request/response DTOs; do not leak internal models.
- Keep contracts backward compatible unless change is explicitly requested.
- Use clear status codes (`2xx`, `400`, `404`, `409`, `5xx`) with consistent semantics.
- Validate payloads with `jakarta.validation` and fail fast on invalid input.

### **Marketplace API Patterns**

**Order Creation Endpoint:**
```java
@PostMapping("/orders")
public OrderResponse createOrder(
    @AuthenticationPrincipal User buyer,
    @Valid @RequestBody CreateOrderRequest request
) {
    // POST /api/v1/orders
    // { items: [{ productId: 123, quantity: 2 }...] }
    // 201 CREATED: { id, buyerId, items, status, totalGross, totalCommission, totalPayout }
    // 400 BAD_REQUEST: validation failed
    // 404 NOT_FOUND: product not found
    // 409 CONFLICT: insufficient stock
}
```

**Seller Earnings Endpoint:**
```java
@GetMapping("/sellers/{sellerId}/earnings")
public SellerEarningsResponse getEarnings(
    @PathVariable Long sellerId,
    @AuthenticationPrincipal User user
) {
    // Only seller or admin can access own earnings
    // Return: { totalEarned, totalPaidOut, pendingPayout, lastPayoutAt }
    // Commission split shown: "You keep 95%, platform keeps 5%"
}
```

**Payment with Commission Split:**
```java
@PostMapping("/orders/{orderId}/pay")
public PaymentResponse processOrderPayment(
    @PathVariable Long orderId,
    @Valid @RequestBody PaymentRequest request,
    @AuthenticationPrincipal User buyer
) {
    // After payment succeeds:
    // 1. Create transaction record with commission split
    // 2. Update seller_earnings (pending_payout += seller's 95%)
    // 3. Update order.status = PAID
    // Return payment confirmation with breakdown
}
```

**Seller Product CRUD with Authorization:**
```java
@PostMapping("/sellers/me/products")
public ProductResponse createProduct(
    @AuthenticationPrincipal User seller,
    @Valid @RequestBody CreateProductRequest request
) {
    // Validates: user.is_seller = true
    // 201 CREATED: { id, sellerId, name, price, stockQuantity, isActive, publishedAt }
    // 403 FORBIDDEN: "You must be a seller to create products"
}

@PutMapping("/sellers/me/products/{productId}")
public ProductResponse updateProduct(
    @PathVariable Long productId,
    @AuthenticationPrincipal User seller,
    @Valid @RequestBody UpdateProductRequest request
) {
    // Validates: product.seller_id = seller.id
    // 200 OK: updated product
    // 404 NOT_FOUND: if product doesn't exist or seller doesn't own it
    // 403 FORBIDDEN: if seller doesn't own this product
}
```

**Commission-Aware Responses:**
```json
{
  "orderId": 123,
  "buyerAmount": 100.00,
  "commission": {
    "rate": "5%",
    "amount": 5.00
  },
  "sellerPayout": 95.00,
  "status": "PAID"
}
```

## Exception Handling Guidance

- Centralize error mapping with `@RestControllerAdvice`.
- Return structured errors with machine-readable `code` and actionable `message`.
- Never expose stack traces or sensitive internals in API responses.

## Testing Guidance

- Prefer JUnit 5 and focused Spring test slices.
- Cover request validation, error mapping, and business paths.
- Keep tests deterministic and isolated.

### **Marketplace-Specific Tests**
- **Commission Calculation**: Test edge cases (rounding, min amounts, zero)
- **Seller Authorization**: Verify seller can only access own products/orders
- **Order Lifecycle**: Happy path (create → pay → ship → deliver) + cancellation paths
- **Stock Management**: Concurrent purchases, overstock prevention
- **Payout Rules**: Validate pending_payout = total_earned - total_paid_out
- **Transaction Recording**: Ensure commission split is accurate after each order
- **Access Control**: Buyer cannot access other buyer's orders; seller cannot see other sellers' data

## Observability Guidance

- Use structured logs with correlation/request trace IDs (MDC where available).
- Do not log secrets or sensitive payload contents.
- Keep actuator exposure minimal and explicit.

### **Marketplace Logging Patterns (Splunk-Ready)**

All endpoints must log progression through 3 flow stages with `flow_stage` key:

```java
// Stage 1: REQUEST_RECEIVED — request arrives
logger.info("flow_stage=REQUEST_RECEIVED operation=order.create buyerId={} itemCount={} status=INITIATED", 
    buyerId, items.size());

// Stage 2: DOMAIN_OPERATION_COMPLETED — business logic finishes
logger.info("flow_stage=DOMAIN_OPERATION_COMPLETED operation=order.create orderId={} totalGross={} status=SUCCESS",
    order.id(), order.totalGross());

// Stage 3: RESPONSE_PREPARED — ready to respond
logger.info("flow_stage=RESPONSE_PREPARED operation=order.create orderId={} status=COMPLETED",
    order.id());
```

**Marketplace Key Metrics to Log:**
- `seller_id` — for seller-specific operations
- `buyer_id` — for buyer operations
- `commission` — commission amount calculated
- `payout` — seller payout amount
- `order_status` — current order state
- `product_id` — product involved
- `operation` — name of what's happening (order.create, payment.process, etc.)

**Example: Complete order flow**
```
flow_stage=REQUEST_RECEIVED operation=order.create buyerId=123 productId=456 qty=2 status=INITIATED
flow_stage=DOMAIN_OPERATION_COMPLETED operation=order.create orderId=789 sellerId=111 grossAmount=100.00 commission=5.00 payout=95.00 status=SUCCESS
flow_stage=RESPONSE_PREPARED operation=order.create orderId=789 status=COMPLETED
```

## Docker and Runtime Hardening

- Prefer minimal base images and non-root runtime users.
- Keep image contents lean and avoid unnecessary packages.
- Configure health checks/readiness consistently with service behavior.

## Security and OWASP Requirements

- No hardcoded credentials, tokens, or secrets.
- Enforce strict input validation and secure defaults.
- Prefer patch/minor dependency upgrades unless incompatibility forces otherwise.
- For dependency/security changes, include:
  - impacted dependency and version
  - CVE and severity
  - minimum fixed version
  - brief risk/compatibility notes

## Output Expectations

When delivering changes, include:
- What changed and why
- Files touched
- Tests and verification commands executed
- Risks, trade-offs, and concrete next steps

---

## Marketplace Implementation Files & References

**Primary Reference:**
- `marketplace.md` — Complete architecture blueprint with Phase 1-3 roadmap

**Key Directories:**
```
hex-core/src/main/java/com/example/user/
├── model/              # Domain models (User, Product, Order, Transaction, etc.)
├── port/in/            # Input ports (CreateOrderPort, EnableSellerPort, etc.)
└── exception/          # Marketplace-specific exceptions

hex-payment-core/src/main/java/com/example/user/
├── model/              # Payment models (PaymentSplit, OrderPayment, etc.)
└── port/in/            # Payment ports (ProcessOrderPaymentPort, etc.)

hex-inbound-adapter-web/src/main/java/com/example/user/
└── api/                # REST controllers (OrderControllerAdapter, SellerControllerAdapter, etc.)

hex-outbound-adapter-db/src/main/java/com/example/user/
└── adapter/            # Repository adapters (OrderRepositoryAdapter, TransactionRepositoryAdapter, etc.)

frontend/src/
├── pages/              # Page components (cart-page.ts, checkout-page.ts, seller-*.ts)
├── components/         # Reusable components (product-card.ts, review-card.ts, etc.)
└── api/                # API client functions (orders.ts, sellers.ts, etc.)
```

**Database Migration Convention:**
- V11-V18 migrations for marketplace tables (sellers, orders, transactions, payouts, reviews)
- Always use `WHERE NOT EXISTS` to make migrations idempotent
- Reference `marketplace.md` for exact SQL schema

**Documentation:**
- `ADMIN_USERS_FEATURE.md` — Reference for role-based access patterns
- `LOGGING_IMPLEMENTATION.md` — Reference for Splunk-ready logging
- `TESTING.md` — Test conventions and patterns
