export async function onRequestGet() {
  return new Response(
    JSON.stringify({ ok: true, service: "insightdeal-relay" }),
    {
      status: 200,
      headers: {
        "Content-Type": "application/json; charset=utf-8",
        "Cache-Control": "no-store, max-age=0",
      },
    }
  );
}
