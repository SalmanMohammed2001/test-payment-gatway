import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { fetchResult, TransactionResult } from '../api';

type Tone = 'success' | 'declined' | 'failed' | 'pending';

function toneFor(status: string): Tone {
  switch (status) {
    case 'CAPTURED':
    case 'AUTHORIZED':
      return 'success';
    case 'DECLINED':
      return 'declined';
    case 'PENDING':
      return 'pending';
    default:
      return 'failed';
  }
}

const HEADLINE: Record<Tone, string> = {
  success: 'Payment successful',
  declined: 'Payment declined',
  failed: 'Payment failed',
  pending: 'Payment pending',
};

export default function ResultPage() {
  const [params] = useSearchParams();
  const ref = params.get('ref');
  const statusFromUrl = params.get('status');
  const messageFromUrl = params.get('message');
  const decisionFromUrl = params.get('decision');

  const [result, setResult] = useState<TransactionResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!ref) {
      setError('No reference provided.');
      setLoading(false);
      return;
    }
    fetchResult(ref)
      .then(setResult)
      .catch((err: unknown) =>
        setError(err instanceof Error ? err.message : 'Could not load result.'),
      )
      .finally(() => setLoading(false));
  }, [ref]);

  const displayStatus = result?.status ?? statusFromUrl ?? 'UNKNOWN';
  const tone = toneFor(displayStatus);
  const displayMessage = result?.providerReason ?? messageFromUrl;
  const displayDecision = result?.providerStatus ?? decisionFromUrl;

  return (
    <div className="page">
      <div className="card">
        {loading && (
          <>
            {statusFromUrl && (
              <div className={`status-badge ${toneFor(statusFromUrl)}`}>{statusFromUrl}</div>
            )}
            <p className="muted">Loading your payment result…</p>
            {messageFromUrl && <div className="alert error">{messageFromUrl}</div>}
          </>
        )}

        {!loading && error && (
          <>
            <div className="status-badge failed">Error</div>
            <h1>Couldn't load result</h1>
            <div className="alert error">{error}</div>
            {messageFromUrl && <div className="alert error">{messageFromUrl}</div>}
            {statusFromUrl && (
              <p className="muted">
                CyberSource status: <strong>{statusFromUrl}</strong>
                {decisionFromUrl ? ` (${decisionFromUrl})` : ''}
              </p>
            )}
          </>
        )}

        {!loading && result && (
          <>
            <div className={`status-badge ${tone}`}>{displayStatus}</div>
            <h1>{HEADLINE[tone]}</h1>

            <dl className="details">
              <div>
                <dt>Reference</dt>
                <dd>{result.referenceCode}</dd>
              </div>
              <div>
                <dt>Amount</dt>
                <dd>
                  {result.amount != null ? result.amount.toFixed(2) : '—'} {result.currency ?? ''}
                </dd>
              </div>
              <div>
                <dt>Transaction ID</dt>
                <dd className="mono">{result.cybersourceId ?? '—'}</dd>
              </div>
              <div>
                <dt>Provider decision</dt>
                <dd>{displayDecision ?? '—'}</dd>
              </div>
              {displayMessage && (
                <div>
                  <dt>Message</dt>
                  <dd>{displayMessage}</dd>
                </div>
              )}
            </dl>
          </>
        )}

        <Link to="/checkout" className="btn primary" style={{ marginTop: '1.5rem' }}>
          Start a new payment
        </Link>
      </div>
    </div>
  );
}
