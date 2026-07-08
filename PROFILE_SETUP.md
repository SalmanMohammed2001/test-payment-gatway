# CyberSource Secure Acceptance — Profile Checklist

Use this after changing settings in the **Business Center (test)**.
Profile: **IP0 test** · ID `7543DEAA-0AED-4C20-BDBA-29302197595D`

## Must do (fixes reason 101 + Processing Error)

### 1. Payment Form tab
- **Checkout Steps → Billing Information:** set to **Disabled**
  (billing is sent from this app as signed `bill_to_*` fields)
- **Payment Information:** enabled
- Click **Save**

### 2. Payment Settings tab
- Card types: **Visa** + **Mastercard** added
- For each card type (⚙️ icon):
  - **CVN Display:** ✓
  - **CVN Required:** ✓
  - **LKR** currency enabled
- Click **Submit** per card type, then **Save**

### 3. Customer Response tab
- **Transaction Response Page:** **Hosted By You**
  - URL: `https://YOUR_PUBLIC_HOST/api/v1/secure-acceptance/response`
  - For local dev use ngrok: `ngrok http 8080` → copy HTTPS URL
- **Custom Redirect After Checkout:**
  - `http://localhost:4200/checkout/result` (local)
  - Or your production frontend URL
- Click **Save**

> Alias also works: `/api/cybersource/webhook` (same handler)

### 4. Security tab
- Confirm **Profile ID**, **Access Key**, and **Secret Key** match `application.yml`
  (or `SA_PROFILE_ID`, `SA_ACCESS_KEY`, `SA_SECRET_KEY` env vars)

### 5. Promote profile ⚠️ CRITICAL
- Click **Promote profile** (top right)
- Until promoted, draft changes are **not** used for live checkouts

## Verify setup URLs

```bash
curl "http://localhost:8080/api/v1/secure-acceptance/setup?publicBaseUrl=https://xxxx.ngrok-free.app"
```

## Run locally

```bash
./run.sh
# Open http://localhost:4200/checkout
# Test card: 4111 1111 1111 1111 · any future expiry · CVN 123
```

## If you still see "Processing Error"

That is a **SYSTEM_ERROR** at authorization — usually the sandbox merchant
(`testingpayment_1783401310`) has no card processor assigned. Contact
CyberSource support to enable card processing on the test account.
