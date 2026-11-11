import { NextResponse } from 'next/server';

const BACKEND_API_URL =
  process.env.BACKEND_API_URL ||
  (process.env.VERCEL === '1' ? 'https://34.59.208.14.sslip.io' : 'http://localhost:8080');

export async function POST(request: Request) {
  try {
    const subscription = await request.json();
    console.log('[API Route] Registering subscription:', subscription);

    // 백엔드 API 서버의 `/api/push/register-web` 엔드포인트로 릴레이
    const response = await fetch(`${BACKEND_API_URL}/api/push/register-web`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(subscription)
    });

    if (response.ok) {
      return NextResponse.json({ success: true });
    } else {
      const errorMsg = await response.text();
      return NextResponse.json({ error: `Backend error: ${errorMsg}` }, { status: response.status });
    }
  } catch (e: unknown) {
    console.error('[API Route] Error registering push subscription:', e);
    const message = e instanceof Error ? e.message : 'Internal Server Error';
    return NextResponse.json({ error: message }, { status: 500 });
  }
}

export async function DELETE(request: Request) {
  try {
    const body = await request.json();
    const { endpoint = '', device_uuid = '' } = body;

    const params = new URLSearchParams();
    if (endpoint) params.set('endpoint', endpoint);
    if (device_uuid) params.set('device_uuid', device_uuid);

    const response = await fetch(`${BACKEND_API_URL}/api/push/register-web?${params.toString()}`, {
      method: 'DELETE'
    });

    if (response.ok) {
      return NextResponse.json({ success: true });
    } else {
      return NextResponse.json({ error: 'Failed to delete from backend' }, { status: response.status });
    }
  } catch (e: unknown) {
    const message = e instanceof Error ? e.message : 'Internal Server Error';
    return NextResponse.json({ error: message }, { status: 500 });
  }
}
