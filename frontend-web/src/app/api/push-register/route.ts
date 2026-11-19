import { NextResponse } from 'next/server';

function getBackendUrl(): string {
  const raw =
    process.env.BACKEND_URL ||
    process.env.BACKEND_API_URL ||
    (process.env.DOCKER_ENV === 'true'
      ? 'http://api:8080'
      : process.env.VERCEL === '1'
        ? 'https://34.59.208.14.sslip.io'
        : 'http://localhost:8080');
  return raw.replace(/\/+$/, '');
}

function buildForwardHeaders(request: Request, includeJsonContentType = false): Record<string, string> {
  const headers: Record<string, string> = {};
  if (includeJsonContentType) {
    headers['Content-Type'] = 'application/json';
  }
  const deviceToken = request.headers.get('x-device-token')?.trim();
  if (deviceToken) {
    headers['X-Device-Token'] = deviceToken;
  }
  return headers;
}

async function parseUpstreamBody(response: Response): Promise<unknown> {
  const rawText = await response.text();
  if (!rawText) return null;
  try {
    return JSON.parse(rawText) as unknown;
  } catch {
    return rawText;
  }
}

export async function POST(request: Request) {
  let subscription: unknown;
  try {
    subscription = await request.json();
  } catch {
    return NextResponse.json({ error: 'Invalid JSON request body' }, { status: 400 });
  }

  try {
    const backendUrl = getBackendUrl();
    const response = await fetch(`${backendUrl}/api/push/register-web`, {
      method: 'POST',
      headers: buildForwardHeaders(request, true),
      body: JSON.stringify(subscription),
      signal: AbortSignal.timeout(8000),
    });

    const parsed = await parseUpstreamBody(response);
    if (response.ok) {
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        return NextResponse.json({ success: true, ...(parsed as Record<string, unknown>) }, { status: response.status });
      }
      return NextResponse.json({ success: true }, { status: response.status });
    }

    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return NextResponse.json(parsed, { status: response.status });
    }
    return NextResponse.json(
      { error: typeof parsed === 'string' && parsed ? parsed : `Backend error (${response.status})` },
      { status: response.status }
    );
  } catch (e: unknown) {
    const isTimeout = e instanceof Error && (e.name === 'TimeoutError' || e.name === 'AbortError');
    const message = e instanceof Error ? e.message : 'Upstream connection failed';
    return NextResponse.json(
      { error: message },
      { status: isTimeout ? 504 : 502 }
    );
  }
}

export async function DELETE(request: Request) {
  try {
    const url = new URL(request.url);
    let endpoint = url.searchParams.get('endpoint') || '';
    let deviceUuid = url.searchParams.get('device_uuid') || '';

    if (!endpoint || !deviceUuid) {
      const body = (await request.json().catch(() => ({}))) as Record<string, unknown>;
      if (!endpoint && typeof body.endpoint === 'string') {
        endpoint = body.endpoint;
      }
      if (!deviceUuid && typeof body.device_uuid === 'string') {
        deviceUuid = body.device_uuid;
      }
    }

    const params = new URLSearchParams();
    if (endpoint) params.set('endpoint', endpoint);
    if (deviceUuid) params.set('device_uuid', deviceUuid);

    const backendUrl = getBackendUrl();
    const querySuffix = params.toString();
    const target = querySuffix
      ? `${backendUrl}/api/push/register-web?${querySuffix}`
      : `${backendUrl}/api/push/register-web`;

    const response = await fetch(target, {
      method: 'DELETE',
      headers: buildForwardHeaders(request, false),
      signal: AbortSignal.timeout(8000),
    });

    const parsed = await parseUpstreamBody(response);
    if (response.ok) {
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        return NextResponse.json({ success: true, ...(parsed as Record<string, unknown>) }, { status: response.status });
      }
      return NextResponse.json({ success: true }, { status: response.status });
    }

    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return NextResponse.json(parsed, { status: response.status });
    }
    return NextResponse.json(
      { error: typeof parsed === 'string' && parsed ? parsed : 'Failed to delete from backend' },
      { status: response.status }
    );
  } catch (e: unknown) {
    const isTimeout = e instanceof Error && (e.name === 'TimeoutError' || e.name === 'AbortError');
    const message = e instanceof Error ? e.message : 'Upstream connection failed';
    return NextResponse.json(
      { error: message },
      { status: isTimeout ? 504 : 502 }
    );
  }
}
