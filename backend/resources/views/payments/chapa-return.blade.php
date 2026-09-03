<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Payment received — EthioLoadAI</title>
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
        .card { max-width: 360px; }
        .check {
            width: 64px;
            height: 64px;
            margin: 0 auto 20px;
            border-radius: 50%;
            background: #F59E0B;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 32px;
        }
        h1 { font-size: 20px; margin: 0 0 8px; }
        p { font-size: 14px; color: #C9D6CC; line-height: 1.5; margin: 0; }
    </style>
</head>
<body>
    @php
        $copy = match ($outcome ?? 'unknown_tx_ref') {
            'paid', 'already_paid' => ['&#10003;', '#F59E0B', 'Payment received', 'You can close this page and return to the EthioLoadAI app — your booking will update automatically.'],
            'marked_failed', 'unknown_tx_ref' => ['&#10005;', '#B3411F', 'Payment not completed', 'It looks like this payment didn\'t go through. Return to the app and try again.'],
            default => ['&#8987;', '#8A5C0A', 'Confirming your payment', 'This is taking a little longer than usual. Return to the app — it will keep checking automatically.'],
        };
    @endphp
    <div class="card">
        <div class="check" style="background: {{ $copy[1] }};">{!! $copy[0] !!}</div>
        <h1>{{ $copy[2] }}</h1>
        <p>{{ $copy[3] }}</p>
    </div>
</body>
</html>
