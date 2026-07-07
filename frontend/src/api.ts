// Thin client for the Secure Acceptance backend endpoints.

export interface CheckoutResponse {
  action: string;
  fields: Record<string, string>;
}

export interface TransactionResult {
  id: number;
  referenceCode: string;
  cybersourceId: string | null;
  transactionType: string;
  status: string;
  providerStatus: string | null;
  providerReason: string | null;
  amount: number | null;
  currency: string | null;
}

export interface CheckoutRequest {
  referenceCode: string;
  amount: number;
  currency: string;
}

async function parseError(res: Response): Promise<string> {
  try {
    const body = await res.json();
    return body.message ?? `Request failed (${res.status})`;
  } catch {
    return `Request failed (${res.status})`;
  }
}

/** Requests signed hosted-checkout fields from the backend. */
export async function createCheckout(req: CheckoutRequest): Promise<CheckoutResponse> {
  const res = await fetch('/api/v1/secure-acceptance/checkout', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  });
  if (!res.ok) throw new Error(await parseError(res));
  return res.json();
}

/** Fetches the final outcome for a reference code. */
export async function fetchResult(referenceCode: string): Promise<TransactionResult> {
  const res = await fetch(
    `/api/v1/secure-acceptance/result?referenceCode=${encodeURIComponent(referenceCode)}`,
  );
  if (!res.ok) throw new Error(await parseError(res));
  return res.json();
}

/**
 * Builds a hidden form from the signed fields and submits it, navigating the
 * browser to the CyberSource hosted payment page. The backend never sees the
 * card data — it goes straight from the customer's browser to CyberSource.
 */
export function postToCyberSource({ action, fields }: CheckoutResponse): void {
  const form = document.createElement('form');
  form.method = 'POST';
  form.action = action;
  for (const [name, value] of Object.entries(fields)) {
    const input = document.createElement('input');
    input.type = 'hidden';
    input.name = name;
    input.value = value;
    form.appendChild(input);
  }
  document.body.appendChild(form);
  form.submit();
}
