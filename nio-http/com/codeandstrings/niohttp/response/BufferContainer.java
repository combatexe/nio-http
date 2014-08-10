package com.codeandstrings.niohttp.response;

import java.nio.ByteBuffer;

public class BufferContainer {

	private ByteBuffer buffer;
	private boolean closeOnTransmission;

	public ByteBuffer getBuffer() {
		return buffer;
	}

	public void setBuffer(ByteBuffer buffer) {
		this.buffer = buffer;
	}

	public boolean isCloseOnTransmission() {
		return closeOnTransmission;
	}

	public void setShouldCloseOnTransmission(boolean closeOnTransmission) {
		this.closeOnTransmission = closeOnTransmission;
	}

	@Override
	public String toString() {
		return "BufferContainer [buffer=" + buffer
				+ ", shouldCloseOnTransmission=" + closeOnTransmission
				+ "]";
	}

	public BufferContainer(ByteBuffer buffer, boolean shouldCloseOnTransmission) {
		super();
		this.buffer = buffer;
		this.closeOnTransmission = shouldCloseOnTransmission;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((buffer == null) ? 0 : buffer.hashCode());
		result = prime * result + (closeOnTransmission ? 1231 : 1237);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		BufferContainer other = (BufferContainer) obj;
		if (buffer == null) {
			if (other.buffer != null)
				return false;
		} else if (!buffer.equals(other.buffer))
			return false;
		if (closeOnTransmission != other.closeOnTransmission)
			return false;
		return true;
	}

}