package com.codeandstrings.niohttp.response;

import com.codeandstrings.niohttp.request.Request;

import java.nio.charset.Charset;

public class StringResponseFactory {

    private Request request;
    private String contentType;
    private String response;

    private Response header;
    private ResponseContent body;

    public Response getHeader() {
        return header;
    }

    public ResponseContent getBody() {
        return body;
    }

    private void fabricateHeader() {
        this.header = ResponseFactory.createResponse(this.contentType, this.body.getBuffer().length, this.request);
    }

    private void fabricateBody() {
        byte bytes[] = this.response.getBytes(Charset.forName("UTF-8"));
        this.body = new ResponseContent(this.request, bytes, true);
    }

    private void fabricate() {
        this.fabricateBody();
        this.fabricateHeader();
        this.body.setResponse(this.header);
    }

    public StringResponseFactory(Request request, String contentType, String response) {

        this.request = request;
        this.contentType = contentType;
        this.response = response;

        this.fabricate();

    }


}
