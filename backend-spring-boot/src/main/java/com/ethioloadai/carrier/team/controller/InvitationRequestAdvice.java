package com.ethioloadai.carrier.team.controller;

import com.ethioloadai.exception.GlobalExceptionHandler.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

/** Parser diagnostics can contain body fragments. Never log or echo token request bodies. */
@RestControllerAdvice(assignableTypes=CompanyInvitationController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InvitationRequestAdvice {
 @ExceptionHandler(HttpMessageNotReadableException.class)
 public ResponseEntity<ErrorResponse> unreadable() {
  return ResponseEntity.badRequest().body(new ErrorResponse("Invalid invitation request", "INVALID_REQUEST_BODY"));
 }
}
