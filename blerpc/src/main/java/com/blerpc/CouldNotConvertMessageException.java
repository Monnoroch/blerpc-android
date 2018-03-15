package com.blerpc;

/** An exception to be thrown if serializing or deserializing bytes to or from messages fails. */
public class CouldNotConvertMessageException extends Exception {

  private CouldNotConvertMessageException(Exception reason) {
    super(reason);
  }

  private CouldNotConvertMessageException(String message) {
    super(message);
  }

  /**
   * Create an exception to be thrown when serializing a request failed.
   *
   * @param reason the exception that caused this exception to be thrown
   * @return the exception
   */
  public static CouldNotConvertMessageException serializeRequest(Exception reason) {
    return new CouldNotConvertMessageException(reason);
  }

  /**
   * Create an exception to be thrown when serializing a request failed.
   *
   * @param format the format string
   * @param args   arguments for the format string
   * @return the exception
   */
  public static CouldNotConvertMessageException serializeRequest(String format, Object... args) {
    return new CouldNotConvertMessageException(String.format("Could not serialize request: " + format, args));
  }

  /**
   * Create an exception to be thrown when deserializing a response failed.
   *
   * @param reason the exception that caused this exception to be thrown
   * @return the exception
   */
  public static CouldNotConvertMessageException deserializeResponse(Exception reason) {
    return new CouldNotConvertMessageException(reason);
  }

  /**
   * Create an exception to be thrown when deserializing a response failed.
   *
   * @param format the format string
   * @param args   arguments for the format string
   * @return the exception
   */
  public static CouldNotConvertMessageException deserializeResponse(String format, Object... args) {
    return new CouldNotConvertMessageException(String.format("Could not deserialize response: " + format, args));
  }
}
