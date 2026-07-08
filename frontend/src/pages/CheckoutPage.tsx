import { FormEvent, useState } from 'react';
import { BillingInfo, createCheckout, postToCyberSource, SUPPORTED_CURRENCY } from '../api';

function defaultReference(): string {
  return `order-${Math.floor(1000 + Math.random() * 9000)}`;
}

// Matches CyberSource Billing Information form fields.
const DEFAULT_BILLING: BillingInfo = {
  firstName: 'John',
  lastName: 'Doe',
  email: 'test@cybs.com',
  address1: '1 Market St',
  address2: 'Suite 100',
  city: 'San Francisco',
  state: 'CA',
  postalCode: '94105',
  country: 'US',
  phone: '4158880000',
};

export default function CheckoutPage() {
  const [referenceCode, setReferenceCode] = useState(defaultReference);
  const [amount, setAmount] = useState('');
  const currency = SUPPORTED_CURRENCY;
  const [billing, setBilling] = useState<BillingInfo>(DEFAULT_BILLING);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function updateBilling(field: keyof BillingInfo, value: string) {
    setBilling((prev) => ({ ...prev, [field]: value }));
  }

  function handleAmountChange(value: string) {
    // Allow digits and a single decimal point while typing (better than type="number").
    const cleaned = value.replace(/[^\d.]/g, '');
    const parts = cleaned.split('.');
    const normalized =
      parts.length <= 1 ? cleaned : `${parts[0]}.${parts.slice(1).join('').slice(0, 2)}`;
    setAmount(normalized);
  }

  function validate(parsed: number): string | null {
    if (!referenceCode.trim() || !Number.isFinite(parsed) || parsed <= 0) {
      return 'Enter a reference and a valid amount greater than 0.';
    }
    const required: [keyof BillingInfo, string][] = [
      ['firstName', 'First Name'],
      ['lastName', 'Last Name'],
      ['address1', 'Street Address 1'],
      ['city', 'City'],
      ['postalCode', 'Zip/Postal Code'],
      ['country', 'Country'],
      ['phone', 'Phone Number'],
      ['email', 'Email'],
    ];
    for (const [key, label] of required) {
      if (!billing[key]?.trim()) {
        return `${label} is required.`;
      }
    }
    if (billing.country.toUpperCase() === 'US' && !billing.state?.trim()) {
      return 'State is required for US addresses.';
    }
    return null;
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    const parsed = Number(amount);
    const validationError = validate(parsed);
    if (validationError) {
      setError(validationError);
      return;
    }
    setSubmitting(true);
    try {
      const checkout = await createCheckout({
        referenceCode: referenceCode.trim(),
        amount: parsed,
        currency,
        billTo: {
          ...billing,
          country: billing.country.trim().toUpperCase(),
        },
      });
      postToCyberSource(checkout);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong.');
      setSubmitting(false);
    }
  }

  return (
    <div className="page">
      <div className="card wide">
        <header className="card-header">
          <span className="brand">Exotic Holiday</span>
          <h1>Secure checkout</h1>
          <p className="muted">
            Enter billing details required by CyberSource, then you'll be taken to their
            secure page for your card. Card details never touch our servers.
          </p>
        </header>

        <form onSubmit={handleSubmit} className="form">
          <label className="field">
            <span>Reference</span>
            <input
              value={referenceCode}
              onChange={(e) => setReferenceCode(e.target.value)}
              placeholder="order-1001"
            />
          </label>

          <div className="row">
            <label className="field grow">
              <span>Amount (LKR)</span>
              <input
                type="text"
                inputMode="decimal"
                autoComplete="off"
                value={amount}
                onChange={(e) => handleAmountChange(e.target.value)}
                placeholder="10000.00"
                disabled={submitting}
              />
            </label>
            <label className="field currency-fixed">
              <span>Currency</span>
              <div className="currency-value">LKR</div>
            </label>
          </div>

          <div className="section-label">Billing Information</div>

          <div className="row">
            <label className="field grow">
              <span>First Name *</span>
              <input
                value={billing.firstName}
                onChange={(e) => updateBilling('firstName', e.target.value)}
                placeholder="John"
                required
              />
            </label>
            <label className="field grow">
              <span>Last Name *</span>
              <input
                value={billing.lastName}
                onChange={(e) => updateBilling('lastName', e.target.value)}
                placeholder="Doe"
                required
              />
            </label>
          </div>

          <label className="field">
            <span>Street Address 1 *</span>
            <input
              value={billing.address1}
              onChange={(e) => updateBilling('address1', e.target.value)}
              placeholder="1 Market St"
              required
            />
          </label>

          <label className="field">
            <span>Street Address 2</span>
            <input
              value={billing.address2 ?? ''}
              onChange={(e) => updateBilling('address2', e.target.value)}
              placeholder="Suite / Apt (optional)"
            />
          </label>

          <div className="row">
            <label className="field grow">
              <span>City *</span>
              <input
                value={billing.city}
                onChange={(e) => updateBilling('city', e.target.value)}
                placeholder="San Francisco"
                required
              />
            </label>
            <label className="field">
              <span>State</span>
              <input
                value={billing.state ?? ''}
                onChange={(e) => updateBilling('state', e.target.value)}
                placeholder="CA"
              />
            </label>
          </div>

          <div className="row">
            <label className="field grow">
              <span>Zip/Postal Code *</span>
              <input
                value={billing.postalCode}
                onChange={(e) => updateBilling('postalCode', e.target.value)}
                placeholder="94105"
                required
              />
            </label>
            <label className="field">
              <span>Country *</span>
              <input
                value={billing.country}
                onChange={(e) => updateBilling('country', e.target.value)}
                placeholder="US"
                maxLength={2}
                required
              />
            </label>
          </div>

          <div className="row">
            <label className="field grow">
              <span>Phone Number *</span>
              <input
                type="tel"
                value={billing.phone}
                onChange={(e) => updateBilling('phone', e.target.value)}
                placeholder="4158880000"
                required
              />
            </label>
            <label className="field grow">
              <span>Email *</span>
              <input
                type="email"
                value={billing.email}
                onChange={(e) => updateBilling('email', e.target.value)}
                placeholder="test@cybs.com"
                required
              />
            </label>
          </div>

          {error && <div className="alert error">{error}</div>}

          <button type="submit" className="btn primary" disabled={submitting}>
            {submitting ? 'Redirecting…' : `Pay ${amount || '0.00'} LKR`}
          </button>
        </form>

        <p className="sandbox-note">
          Sandbox mode · test card <code>4111 1111 1111 1111</code> · CVN <code>123</code>
        </p>
      </div>
    </div>
  );
}
