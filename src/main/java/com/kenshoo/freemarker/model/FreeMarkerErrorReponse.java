package com.kenshoo.freemarker.model;

/**
 * Created by Pmuruge on 8/30/2015.
 */
public class FreeMarkerErrorReponse {
    private FreeMarkerError errorCode;
    private String errorDescription;

    public FreeMarkerErrorReponse(FreeMarkerError errorCode, String errorDescription) {
        this.errorCode = errorCode;
        this.errorDescription = errorDescription;
    }
}
