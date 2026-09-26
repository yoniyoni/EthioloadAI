package com.ethioloadai.carrier.team.exception;
import com.ethioloadai.exception.BaseException;
public class CompanyTeamException extends BaseException {
 public CompanyTeamException(String message, String code, int status) { super(message, code, status); }
 public static CompanyTeamException forbidden() { return new CompanyTeamException("Company owner access required", "COMPANY_ACCESS_DENIED", 403); }
 public static CompanyTeamException invalid(String message) { return new CompanyTeamException(message, "INVALID_TEAM_OPERATION", 400); }
 public static CompanyTeamException invalidToken() { return new CompanyTeamException("Invitation is invalid or no longer available", "INVITATION_INVALID", 400); }
 public static CompanyTeamException mail() { return new CompanyTeamException("Invitation email could not be sent. Please try again later.", "INVITATION_EMAIL_FAILED", 503); }
}

