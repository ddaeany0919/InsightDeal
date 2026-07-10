import { NextResponse } from 'next/server';

const BACKEND_API_URL = process.env.BACKEND_API_URL || 'http://localhost:8080';

export async function POST(request: Request) {
  try {
    const subscription = await request.json();
    console.log('[API Route] Registering subscription:', subscription);

    // 백엔드 API 서버의 `/api/notifications/register-web` 엔드포인트로 릴레이
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
  } catch (e: any) {
    console.error('[API Route] Error registering push subscription:', e);
    return NextResponse.json({ error: e.message || 'Internal Server Error' }, { status: 500 });
  }
}

export async function DELETE(request: Request) {
  try {
    const body = await request.json();
    const { endpoint } = body;

    const response = await fetch(`${BACKEND_API_URL}/api/push/register-web?endpoint=${encodeURIComponent(endpoint)}`, {
      method: 'DELETE'
    });

    if (response.ok) {
      return NextResponse.json({ success: true });
    } else {
      return NextResponse.json({ error: 'Failed to delete from backend' }, { status: response.status });
    }
  } catch (e: any) {
    return NextResponse.json({ error: e.message || 'Internal Server Error' }, { status: 500 });
  }
}
