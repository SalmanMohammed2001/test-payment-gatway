import { FormEvent, useState } from 'react';
import { createCheckout, postToCyberSource } from '../api';

const CURRENCIES = ['USD', 'EUR', 'GBP', 'INR', 'AUD'];

function defaultReference(): string {
  return `order-${Math.floor(1000 + Math.random() * 9000)}`;
}

export default function CheckoutPage() {
  const [referenceCode, setReferenceCode] = useState(defaultReference);
  const [amount, setAmount] = useState('102.21');
  const [currency, setCurrency] = useState('USD');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    const parsed = Number(amount);
    if (!referenceCode.trim() || !Number.isFinite(parsed) || parsed <= 0) {
      setError('Enter a reference and an amount greater than 0.');
      return;
    }
    setSubmitting(true);
    try {
      const checkout = await createCheckout({
        referenceCode: referenceCode.trim(),
        amount: parsed,
        currency,
      });
      // Redirects the browser to the CyberSource hosted payment page.
      postToCyberSource(checkout);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong.');
      setSubmitting(false);
    }
  }

  return (
    <div className="page">
      <div className="card">
        <header className="card-header">
          <span className="brand">Exotic Holiday</span>
          <h1>Secure checkout</h1>
          <p className="muted">
            You'll be taken to CyberSource's secure page to enter your card. Your card
            details never touch our servers.
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
              <span>Amount</span>
              <input
                type="number"
                min="0.01"
                step="0.01"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
              />
            </label>
            <label className="field">
              <span>Currency</span>
              <select value={currency} onChange={(e) => setCurrency(e.target.value)}>
                {CURRENCIES.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </label>
          </div>

          {error && <div className="alert error">{error}</div>}

          <button type="submit" className="btn primary" disabled={submitting}>
            {submitting ? 'Redirecting…' : `Pay ${amount || '0.00'} ${currency}`}
          </button>
        </form>

        <p className="sandbox-note">
          Sandbox mode · test card <code>4111 1111 1111 1111</code>
        </p>
      </div>
    </div>
  );
}
