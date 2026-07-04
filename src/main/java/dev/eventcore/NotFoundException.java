package dev.eventcore;

class NotFoundException extends RuntimeException {

    NotFoundException(String message) {
        super(message);
    }
}
