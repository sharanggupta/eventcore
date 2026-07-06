package dev.eventcore.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every error into the one documented shape, {@code {"error": "..."}} — both the domain
 * exceptions and the framework ones (malformed body, bad path/param, wrong method or media type,
 * unknown route), with a final catch-all so nothing escapes as a raw ProblemDetail.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError invalidRequest(InvalidRequestException exception) {
        return new ApiError(exception.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ApiError unauthorized(UnauthorizedException exception) {
        return new ApiError(exception.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiError notFound(NotFoundException exception) {
        return new ApiError(exception.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError conflict(ConflictException exception) {
        return new ApiError(exception.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError unreadableBody(HttpMessageNotReadableException exception) {
        return new ApiError("request body is not valid JSON");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError typeMismatch(MethodArgumentTypeMismatchException exception) {
        return new ApiError("\"" + exception.getName() + "\" has an invalid value");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiError noSuchRoute(NoResourceFoundException exception) {
        return new ApiError("no such route");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    ApiError methodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        return new ApiError("method not allowed on this route");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    ApiError unsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        return new ApiError("Content-Type must be application/json");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    ApiError unexpected(Exception exception) {
        log.error("Unhandled exception serving a request", exception);
        return new ApiError("internal error");
    }
}
