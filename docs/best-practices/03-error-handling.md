# Error Handling Standards

Consistent error handling across all layers with domain-specific exceptions and a uniform API error response format.

## Exception Hierarchy

| Layer | Base Class | Purpose |
|-------|-----------|---------|
| **Domain** | `DomainException` | Business rule violations (e.g., invalid amount, insufficient data) |
| **Application** | `ApplicationException` | Use case failures (e.g., entity not found, unauthorized action) |
| **Infrastructure** | Standard framework exceptions | Technical errors (e.g., database connection, network timeout) |

## Domain Exceptions — Business Meaning

```java
public class InsufficientTransactionDataException extends DomainException {
    public InsufficientTransactionDataException(String detail) {
        super("Transaction evaluation failed: " + detail);
    }
}

public class InvalidFraudScoreException extends DomainException {
    public InvalidFraudScoreException(double score) {
        super("Fraud score must be between 0 and 100, received: " + score);
    }
}
```

## Application Exceptions — Use Case Failures

```java
public class TransactionNotFoundException extends ApplicationException {
    public TransactionNotFoundException(TransactionId id) {
        super("Transaction not found: " + id.value());
    }
}
```

## API Error Response Format

All REST endpoints return errors in a consistent JSON structure:

```json
{
  "errorCode": "DOMAIN_ERROR",
  "message": "Fraud score must be between 0 and 100, received: 150",
  "timestamp": "2026-07-15T12:00:00Z",
  "correlationId": "tx-123456",
  "details": []
}
```

## Global Exception Handler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomain(DomainException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(new ErrorResponse("DOMAIN_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ErrorResponse> handleApplication(ApplicationException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
```

## HTTP Status Mapping

| Exception Type | HTTP Status | errorCode |
|---------------|-------------|-----------|
| `DomainException` | 422 Unprocessable Entity | `DOMAIN_ERROR` |
| `ApplicationException` | 404 Not Found | `NOT_FOUND` |
| `AccessDeniedException` | 403 Forbidden | `FORBIDDEN` |
| `ValidationException` | 400 Bad Request | `VALIDATION_ERROR` |
| Unhandled exceptions | 500 Internal Server Error | `INTERNAL_ERROR` |
