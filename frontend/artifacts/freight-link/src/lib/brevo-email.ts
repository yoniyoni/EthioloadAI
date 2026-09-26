/**
 * brevo-email.ts
 *
 * Sends invitation emails via the local Vite dev-server mailer plugin
 * (POST /mailer/send-invitation), which relays them through Brevo SMTP
 * using nodemailer on the server side.
 *
 * This keeps SMTP credentials out of the browser bundle and avoids
 * the CORS restrictions that block direct Brevo API calls from the browser.
 */

const MAILER_ENDPOINT = "/mailer/send-invitation";

export interface MailerSendResult {
  messageId: string;
  accepted: string[];
}

/**
 * Send an email through the local mailer proxy.
 * Throws an Error with a human-readable message if the send fails.
 */
export async function sendEmail(params: {
  to: string;
  subject: string;
  htmlContent: string;
  textContent: string;
}): Promise<MailerSendResult> {
  const res = await fetch(MAILER_ENDPOINT, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(params),
  });

  if (!res.ok) {
    let message = `Mailer error ${res.status}`;
    try {
      const json = await res.json();
      message = json?.error ?? message;
    } catch { /* ignore */ }
    throw new Error(message);
  }

  return res.json() as Promise<MailerSendResult>;
}

/**
 * Send a fleet-company team invitation email.
 *
 * @param recipientEmail  The invitee's email address.
 * @param companyName     The company that is inviting them.
 * @param roleName        Human-readable role e.g. "Fleet Manager".
 * @param acceptUrl       Full URL the invitee clicks to accept.
 * @param expiresAt       ISO date string for the expiry time.
 */
export async function sendInvitationEmail(
  recipientEmail: string,
  companyName: string,
  roleName: string,
  acceptUrl: string,
  expiresAt: string,
): Promise<MailerSendResult> {
  const safeName = companyName.replace(/[<>&"]/g, " ");
  const safeRole = roleName.replace(/[<>&"]/g, " ");
  const expiry = new Date(expiresAt).toLocaleString("en-US", {
    dateStyle: "long",
    timeStyle: "short",
  });

  const subject = `You're invited to join ${safeName} on Ethio-Freight`;

  const textContent =
    `You have been invited to join ${safeName} on Ethio-Freight as ${safeRole}.\n\n` +
    `Sign in (or create an account) using this email address, then click the link below to accept.\n\n` +
    `Accept invitation:\n${acceptUrl}\n\n` +
    `This link expires on ${expiry}.\n\n` +
    `If you were not expecting this invitation, please ignore this email.`;

  const htmlContent = `<!DOCTYPE html>
<html lang="en">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="margin:0;padding:0;font-family:Arial,sans-serif;background:#f4f4f4">
  <table width="100%" cellpadding="0" cellspacing="0" style="background:#f4f4f4;padding:32px 0">
    <tr><td align="center">
      <table width="560" cellpadding="0" cellspacing="0"
             style="background:#ffffff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.08)">
        <tr>
          <td style="background:#0c3a2d;padding:28px 32px">
            <p style="margin:0;font-size:22px;font-weight:700;color:#ffffff">&#x1F69B; Ethio-Freight</p>
          </td>
        </tr>
        <tr>
          <td style="padding:32px">
            <h2 style="margin:0 0 12px;font-size:20px;color:#111827">
              You're invited to join <strong>${safeName}</strong>
            </h2>
            <p style="margin:0 0 20px;color:#4b5563;font-size:15px;line-height:1.6">
              <strong>${safeName}</strong> has invited you to join their fleet as
              <strong>${safeRole}</strong>.
            </p>
            <p style="margin:0 0 28px;color:#4b5563;font-size:15px;line-height:1.6">
              Sign in or create an account using <strong>${recipientEmail}</strong>,
              then click the button below to accept.
            </p>
            <table cellpadding="0" cellspacing="0">
              <tr>
                <td style="border-radius:6px;background:#0c3a2d">
                  <a href="${acceptUrl}"
                     style="display:inline-block;padding:14px 28px;font-size:15px;font-weight:600;
                            color:#ffffff;text-decoration:none;border-radius:6px">
                    Accept Invitation
                  </a>
                </td>
              </tr>
            </table>
            <p style="margin:20px 0 0;color:#6b7280;font-size:12px">
              Or copy this link into your browser:<br>
              <a href="${acceptUrl}" style="color:#0c3a2d;word-break:break-all">${acceptUrl}</a>
            </p>
          </td>
        </tr>
        <tr>
          <td style="background:#f9fafb;padding:20px 32px;border-top:1px solid #e5e7eb">
            <p style="margin:0;color:#9ca3af;font-size:12px">
              This invitation expires on <strong>${expiry}</strong>.<br>
              If you were not expecting this, please ignore it.
            </p>
          </td>
        </tr>
      </table>
    </td></tr>
  </table>
</body>
</html>`;

  return sendEmail({ to: recipientEmail, subject, htmlContent, textContent });
}
