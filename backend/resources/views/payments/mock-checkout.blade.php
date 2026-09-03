<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Confirm Payment — EthioLoadAI</title>
    <style>
        body {
            margin: 0;
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            background: #0F3D1A;
            color: #F8FBF8;
            font-family: -apple-system, "Segoe UI", Roboto, sans-serif;
            text-align: center;
            padding: 24px;
            box-sizing: border-box;
        }
        .card { max-width: 360px; width: 100%; }
        .badge {
            display: inline-block;
            font-size: 11px;
            letter-spacing: 0.06em;
            text-transform: uppercase;
            color: #0F3D1A;
            background: #F59E0B;
            padding: 4px 10px;
            border-radius: 999px;
            margin-bottom: 16px;
        }
        h1 { font-size: 20px; margin: 0 0 4px; }
        .amount { font-size: 34px; font-weight: 700; margin: 12px 0 4px; }
        p { font-size: 13px; color: #C9D6CC; line-height: 1.5; margin: 0 0 20px; }
        button {
            width: 100%;
            padding: 14px;
            border: none;
            border-radius: 10px;
            background: #F59E0B;
            color: #0F3D1A;
            font-size: 15px;
            font-weight: 700;
            cursor: pointer;
        }
        button:active { opacity: 0.85; }
        .not-found { color: #E9A594; }
    </style>
</head>
<body>
    <div class="card">
        @if ($found)
            <span class="badge">Simulated Checkout</span>
            <h1>Confirm Payment</h1>
            <div class="amount">ETB {{ number_format($amount, 0) }}</div>
            <p>
                No real money moves here — this stands in for Chapa's checkout
                page until a live account is connected. Tap Confirm to simulate
                a successful payment.
            </p>
            <form method="POST" action="{{ route('mock.confirm') }}">
                <input type="hidden" name="tx_ref" value="{{ $txRef }}">
                <button type="submit">Confirm Payment</button>
            </form>
        @else
            <h1 class="not-found">Payment not found</h1>
            <p>This checkout link is no longer valid. Return to the app and try again.</p>
        @endif
    </div>
</body>
</html>
