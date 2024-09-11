package org.userservice.userservice.expections;

public class WrongPasswordEception extends Exception{
    public WrongPasswordEception(String message) {
        super(message);
    }
}
