package com.bank.money_transfer.exception;

import com.bank.money_transfer.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(ex.getStatus()), ex.getMessage());
        pd.setType(URI.create(ex.getType()));
        pd.setTitle(ex.getTitle());
        pd.setInstance(URI.create(request.getRequestURI()));
        pd.setProperty("traceId", UUID.randomUUID().toString());
        return pd;
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ProblemDetail handleNotFound(AccountNotFoundException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("https://errors.bank.local/account-not-found"));
        pd.setTitle("Account not found");
        pd.setInstance(URI.create(request.getRequestURI()));
        pd.setProperty("traceId", UUID.randomUUID().toString());
        return pd;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleParseError(HttpMessageNotReadableException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request body is malformed or unreadable");
        pd.setType(URI.create("https://errors.bank.local/malformed-request"));
        pd.setTitle("Malformed request body");
        pd.setInstance(URI.create(request.getRequestURI()));
        pd.setProperty("traceId", UUID.randomUUID().toString());
        return pd;
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Content-Type ต้องเป็น application/json");
        pd.setType(URI.create("https://errors.bank.local/unsupported-media-type"));
        pd.setTitle("Unsupported media type");
        pd.setInstance(URI.create(request.getRequestURI()));
        pd.setProperty("traceId", MDC.get(RequestIdFilter.MDC_KEY));
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error occurred");
        pd.setType(URI.create("https://errors.bank.local/internal-error"));
        pd.setTitle("Internal server error");
        pd.setInstance(URI.create(request.getRequestURI()));
        pd.setProperty("traceId", MDC.get(RequestIdFilter.MDC_KEY));
        return pd;
    }
}
