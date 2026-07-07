# CyberSource Payment Service

A Spring Boot 3 service that processes card payments through the **CyberSource REST API**
and persists every transaction to **MySQL**.

## Tech stack

- Java 21, Spring Boot 3.3.5 (Web, Data JPA, Validation)
- MySQL (`mysql-connector-j`)
- CyberSource REST SDK `com.cybersource:cybersource-rest-client-java:0.0.91`
- Lombok

## Features

- **Authorize** or **Sale** (authorize + capture) a card payment
- **Capture** a prior authorization (supports partial capture)
- **Refund** a captured payment (full or partial)
- Every operation stored as a `payment_transaction` row (status, amount, provider status, raw response)
- Centralized validation + error handling

## Configuration

Credentials and DB settings are read from environment variables (see `src/main/resources/application.yml`).

| Variable | Description | Default |
| --- | --- | --- |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | MySQL location & schema | `localhost` / `3306` / `cybersource_payment` |
| `DB_USERNAME` / `DB_PASSWORD` | MySQL credentials | `root` / `root` |
| `CYBS_MERCHANT_ID` | CyberSource merchant id | — |
| `CYBS_MERCHANT_KEY_ID` | REST shared-secret key id | — |
| `CYBS_MERCHANT_SECRET_KEY` | REST shared-secret key | — |
| `CYBS_RUN_ENVIRONMENT` | `apitest.cybersource.com` (sandbox) or `api.cybersource.com` (prod) | sandbox |
| `CYBS_DEFAULT_CURRENCY` | Fallback currency | `USD` |

Generate the REST key id/secret in the CyberSource Business Center under
**Payment Configuration → Key Management → REST Shared Secret**.

The schema is auto-created (`createDatabaseIfNotExist=true` + Hibernate `ddl-auto=update`).

## Run

```bash
export CYBS_MERCHANT_ID=your_merchant_id
export CYBS_MERCHANT_KEY_ID=your_key_id
export CYBS_MERCHANT_SECRET_KEY=your_secret_key

mvn spring-boot:run
# or
mvn -DskipTests package && java -jar target/cybersource-payment-0.0.1-SNAPSHOT.jar
```

## API

Base path: `/api/v1/payments`

### Create a payment (sale)

```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{
    "referenceCode": "order-1001",
    "amount": 102.21,
    "currency": "USD",
    "capture": true,
    "card": {
      "number": "4111111111111111",
      "expirationMonth": "12",
      "expirationYear": "2031",
      "securityCode": "123"
    },
    "billTo": {
      "firstName": "John",
      "lastName": "Doe",
      "address1": "1 Market St",
      "locality": "San Francisco",
      "administrativeArea": "CA",
      "postalCode": "94105",
      "country": "US",
      "email": "john.doe@example.com",
      "phoneNumber": "4158880000"
    }
  }'
```

Set `"capture": false` to only authorize, then capture later.

### Capture an authorization

```bash
curl -X POST http://localhost:8080/api/v1/payments/{cybersourceId}/captures \
  -H "Content-Type: application/json" \
  -d '{ "referenceCode": "order-1001", "amount": 102.21, "currency": "USD" }'
```

### Refund a payment

```bash
curl -X POST http://localhost:8080/api/v1/payments/{cybersourceId}/refunds \
  -H "Content-Type: application/json" \
  -d '{ "referenceCode": "order-1001", "amount": 102.21, "currency": "USD" }'
```

### Look up transactions

```bash
curl http://localhost:8080/api/v1/payments/{id}                       # by DB id
curl "http://localhost:8080/api/v1/payments?referenceCode=order-1001" # by reference
```

`{cybersourceId}` is the `cybersourceId` returned by the create/authorize call.

## Project layout

```
src/main/java/com/exotic/payment
├── CybersourcePaymentApplication.java
├── config/       # CyberSource properties + ApiClient factory
├── controller/   # REST endpoints
├── domain/       # JPA entity + enums
├── dto/          # request/response records
├── exception/    # custom exceptions + global handler
├── repository/   # Spring Data repository
└── service/      # CyberSource integration logic
```

## Notes

- Test card `4111111111111111` works in the CyberSource sandbox.
- The CyberSource SDK is officially validated up to Java 19 but compiles and runs on Java 21.
- Never commit real merchant secrets; `.env`, `application-local.yml`, and `application-secret.yml` are git-ignored.
```
