package dev.eventcore;

class UnauthorizedException extends RuntimeException {

    UnauthorizedException(String message) {
        super(message);
    }
}
