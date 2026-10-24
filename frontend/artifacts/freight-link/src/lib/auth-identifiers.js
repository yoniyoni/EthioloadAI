const BACKEND_PHONE_PATTERN = /^\+?[0-9]{10,15}$/;

export function normalizeEthiopianPhone(value) {
  const phone = value.replace(/[\s()-]/g, "");
  if (/^[79]\d{8}$/.test(phone)) return "+251" + phone;
  if (/^0[79]\d{8}$/.test(phone)) return "+251" + phone.slice(1);
  if (/^251[79]\d{8}$/.test(phone)) return "+" + phone;
  return phone;
}

export function isValidPhoneIdentifier(value) {
  return BACKEND_PHONE_PATTERN.test(normalizeEthiopianPhone(value));
}

export function getLoginIdentifier(identifier, usePhone) {
  return usePhone ? normalizeEthiopianPhone(identifier) : identifier;
}
