package com.codeandstrings.niohttp;

import java.net.URI;

import com.codeandstrings.niohttp.enums.HttpProtocol;
import com.codeandstrings.niohttp.enums.RequestMethod;
import com.codeandstrings.niohttp.exceptions.http.BadRequestException;
import com.codeandstrings.niohttp.exceptions.http.HttpException;
import com.codeandstrings.niohttp.exceptions.http.HttpVersionNotSupported;
import com.codeandstrings.niohttp.exceptions.http.MethodNotAllowedException;

public class RequestHeaderFactory {

	private int receivedLineCount;
	private RequestMethod method;
	private URI uri;
	private HttpProtocol protocol;
	private boolean previousLineWasStartCommand;
	
	public RequestHeaderFactory() {
		this.receivedLineCount = 0;
		this.previousLineWasStartCommand = false;
	}
	
	public RequestHeader build() {
		
		RequestHeader r = new RequestHeader();
		
		r.setMethod(this.method);
		r.setUri(this.uri);
		r.setProtocol(this.protocol);
		
		return r;
		
	}
	
	public void addLine(String line) throws HttpException {
		
		// reset previous line indication
		this.previousLineWasStartCommand = false;
		
		// accept line
		if (this.receivedLineCount == 0) {
			
			this.extractMethod(line);
			this.extractUri(line);
			this.extractProtocol(line);
			
		} else {
			if (line.length() == 0) {
				this.previousLineWasStartCommand = true;
			} else {
				// this is another header line
			}
		}
		
		this.receivedLineCount++;
		
	}
	
	private String[] getInitialLineTokens(String line) throws BadRequestException  {
	
		if (line == null)
			throw new BadRequestException();
		
		if (line.length() == 0)
			throw new BadRequestException();
		
		String tokens[] = line.split(" ");
		
		if (tokens.length < 2) {
			throw new BadRequestException();
		}
		
		return tokens;
	}
	
	public void extractMethod(String line) throws HttpException {
		
		String tokens[] = getInitialLineTokens(line);
		
		if (tokens[0].equalsIgnoreCase("get")) {
			this.method = RequestMethod.GET;
		} else if (tokens[0].equalsIgnoreCase("post")) {
			this.method = RequestMethod.POST;
		} else {
			throw new MethodNotAllowedException(tokens[0]);
		}
				
	}
	
	public void extractUri(String line) throws HttpException {
		
		String tokens[] = getInitialLineTokens(line);
		String uriString = tokens[1];
		
		try {
			this.uri = new URI(uriString);
		} catch (Exception e) {
			throw new BadRequestException();
		}
				
	}
	
	public void extractProtocol(String line) throws HttpException {
		
		String tokens[] = getInitialLineTokens(line);
		
		if (tokens.length != 3)
			return;
				
		if (tokens[2].equalsIgnoreCase("http/1.0")) {
			this.protocol = HttpProtocol.HTTP1_0;
		} else if (tokens[2].equalsIgnoreCase("http/1.1")) {
			this.protocol = HttpProtocol.HTTP1_1;
		} else {
			throw new HttpVersionNotSupported(tokens[2]);
		}
				
	}
	
	public boolean shouldBuildRequestHeader() {
		
		if (this.method == null)
			// not ready to make a request
			return false;
		else if (this.uri == null)
			// not ready to make a request
			return false;
		else if (this.protocol == null) {			
			// this is the most trivial request; it's basically
			// just a GET /uri so should be honored right away 
			// with minimal fuss and no keep alive.
			//
			this.protocol = HttpProtocol.HTTP1_0;
			return true;
		} else {
			// was the last line sent a start command (carriage return on new line)?
			// if so, indicate the request should be built and go
			// otherwise keep accepting new data...
			//
			if (this.previousLineWasStartCommand) { 
				return true;
			} else {
				return false;
			}
		}
		 		
	}
	
}
