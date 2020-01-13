package com.kbeanie.multipicker.api.entity;

/**
 * Contains details about files that were not successful
 */
public class ErrorFile extends ChosenFile {
    private String data;

    public ErrorFile(String data) {
        this.data = data;
    }

    public String getData() {
        return data;
    }
}
