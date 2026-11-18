package io.interviewcodi.backend.user;

public class DeletedUserException extends RuntimeException {

    public DeletedUserException(long userId) {
        super("Deleted user cannot log in: " + userId);
    }
}
