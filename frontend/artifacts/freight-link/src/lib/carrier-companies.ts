import { api } from "@/lib/api";

export type CompanyStatus = "DRAFT" | "PENDING" | "APPROVED" | "REJECTED" | "MORE_INFORMATION_REQUIRED";
export type CompanyForm = {
  companyName: string; legalName: string; tinNumber: string; businessRegistrationNumber: string;
  phone: string; email: string; businessAddress: string; city: string;
};
export type Company = CompanyForm & { id: number; ownerId: number; verificationStatus: CompanyStatus; reviewMessage?: string };
export type CompanyDocument = { id: number; documentType: string; originalName: string; verificationStatus: CompanyStatus; reviewMessage?: string };
export type CompanyPage = { content: Company[]; last: boolean; totalElements: number };
export const blankCompany: CompanyForm = { companyName: "", legalName: "", tinNumber: "", businessRegistrationNumber: "", phone: "", email: "", businessAddress: "", city: "" };
export const companyFields: { key: keyof CompanyForm; label: string; max: number; type?: string; pattern?: string }[] = [
  { key: "companyName", label: "Company name", max: 255 },
  { key: "legalName", label: "Legal name (optional)", max: 255 },
  { key: "tinNumber", label: "TIN number", max: 50, pattern: "[A-Za-z0-9][A-Za-z0-9\\/\\-]{1,49}" },
  { key: "businessRegistrationNumber", label: "Business registration number", max: 100, pattern: "[A-Za-z0-9][A-Za-z0-9\\/\\-]{1,99}" },
  { key: "phone", label: "Company phone", max: 16, type: "tel", pattern: "\\+?[0-9]{7,15}" },
  { key: "email", label: "Company email", max: 255, type: "email" },
  { key: "businessAddress", label: "Business address", max: 1000 },
  { key: "city", label: "City", max: 100 },
];
export const requiredCompanyDocs = [
  { type: "BUSINESS_LICENSE", label: "Business license" },
  { type: "BUSINESS_REGISTRATION", label: "Business registration certificate" },
  { type: "TIN_CERTIFICATE", label: "TIN certificate" },
];
export const statusLabel: Record<CompanyStatus, string> = {
  DRAFT: "Draft", PENDING: "Under review", APPROVED: "Approved", REJECTED: "Rejected", MORE_INFORMATION_REQUIRED: "Changes requested",
};
export const statusHelp: Record<CompanyStatus, string> = {
  DRAFT: "Complete your company details and upload all three documents, then submit for review. You can save and return later.",
  PENDING: "Your application is with the admin team. Details and documents are locked while it is reviewed. This page checks for status updates automatically.",
  APPROVED: "Your company registration is approved. You can now invite fleet managers and dispatchers.",
  REJECTED: "This application was rejected and cannot be edited. Read the review feedback below.",
  MORE_INFORMATION_REQUIRED: "Read the admin feedback, update your details or replace documents, then submit again.",
};
export function companyPayload(form: CompanyForm) {
  return Object.fromEntries(Object.entries(form).map(([key, value]) => [key, value.trim() || null]));
}
export async function downloadCompanyDocument(companyId: number, document: CompanyDocument) {
  const blob = await api.getBlob(`/carrier-companies/${companyId}/documents/${document.id}/file`);
  const url = URL.createObjectURL(blob);
  const link = window.document.createElement("a");
  link.href = url; link.download = document.originalName; link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
