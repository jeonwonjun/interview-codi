package io.interviewcodi.backend.user.domain;

public class DeletedUserException extends RuntimeException {

    public DeletedUserException(long userId) {
        super("Deleted user cannot log in: " + userId);
    }
}
