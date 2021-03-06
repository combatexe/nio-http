package com.codeandstrings.niohttp.handlers.files;

import com.codeandstrings.niohttp.data.DateUtils;
import com.codeandstrings.niohttp.data.FileUtils;
import com.codeandstrings.niohttp.data.IdealBlockSize;
import com.codeandstrings.niohttp.request.Request;
import com.codeandstrings.niohttp.response.Response;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

class FileRequestObject {

    private AsynchronousFileChannel fileChannel;
    private Future<Integer> future;
    private ByteBuffer readBuffer;
    private int position;
    private long fileSize;
    private String mimeType;
    private Date lastModified;
    private String etag;
    private Request request;
    private Response response;
    private long nextSequence;

    public FileRequestObject(Path path, String mimeType, Request request) throws IOException {
        this.fileChannel = AsynchronousFileChannel.open(path, StandardOpenOption.READ);
        this.fileSize = Files.size(path);
        this.position = 0;
        this.mimeType = mimeType;
        this.request = request;
        this.nextSequence = 0;
        this.lastModified = new Date(Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis());
        this.etag = FileUtils.computeEtag(path.getFileName().toString(), this.lastModified);
    }

    public void close() {
        try {
            this.fileChannel.close();
        } catch (Exception e) {}
    }

    public Response getResponse() {
        return response;
    }

    public void setResponse(Response response) {
        this.response = response;
    }

    public Date getLastModified() {
        return lastModified;
    }

    public String getEtag() {
        return etag;
    }

    public Request getRequest() {
        return request;
    }

    public long getNextSequence() {
        this.nextSequence++;
        return this.nextSequence;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getMimeType() {
        return mimeType;
    }

    public boolean isBufferReady() throws ExecutionException, InterruptedException {

        if (!this.readBuffer.hasRemaining()) {
            return true;  // buffer is full
        } else if (this.future.get() == -1) {
            return true;  // buffer isn't full but last call was EOF
        } else if (this.fileSize - this.position - this.future.get() == 0) {
            return true;  // buffer isn't full but there is no more data
        } else {
            return false; // buffer isn't ready
        }

    }

    public ByteBuffer getBuffer() {
        return (ByteBuffer)this.readBuffer.flip();
    }

    public boolean isReadCompleted() throws ExecutionException, InterruptedException {
        return this.fileSize - this.position - this.future.get() == 0;
    }

    public void readNextBuffer() throws ExecutionException, InterruptedException {

        if (this.future != null) {
            this.position = this.position + this.future.get().intValue();
        }

        this.readBuffer = ByteBuffer.allocate(IdealBlockSize.MAX_BUFFER);
        this.future = this.fileChannel.read(this.readBuffer, position);
    }

    public boolean isNotModified(Request r) {

        String modifiedSince = r.getHeaderCaseInsensitive("If-Modified-Since");
        String etagMatch = r.getHeaderCaseInsensitive("If-None-Match");

        if (etagMatch != null && this.getEtag() != null && etagMatch.equals(this.getEtag())) {
            return true;
        }

        if (modifiedSince != null) {

            Date dateObject = DateUtils.parseRfc822DateString(modifiedSince);

            if (dateObject == null) {
                return false;
            }

            if (this.getLastModified().getTime() > dateObject.getTime()) {
                return false;
            } else {
                return true;
            }

        }

        return false;

    }

}
