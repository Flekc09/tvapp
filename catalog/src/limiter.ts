interface Waiter { host: string; start: () => void }

export function createLimiter(global: number, perHost: number) {
  let active = 0;
  const perHostActive = new Map<string, number>();
  const queue: Waiter[] = [];

  function canStart(host: string): boolean {
    return active < global && (perHostActive.get(host) ?? 0) < perHost;
  }
  function pump() {
    for (let i = 0; i < queue.length; ) {
      const w = queue[i];
      if (canStart(w.host)) { queue.splice(i, 1); w.start(); } else i++;
      if (active >= global) break;
    }
  }
  function run<T>(host: string, fn: () => Promise<T>): Promise<T> {
    return new Promise<T>((resolve, reject) => {
      const start = () => {
        active++; perHostActive.set(host, (perHostActive.get(host) ?? 0) + 1);
        fn().then(resolve, reject).finally(() => {
          active--; perHostActive.set(host, (perHostActive.get(host) ?? 1) - 1);
          pump();
        });
      };
      if (canStart(host)) start(); else queue.push({ host, start });
    });
  }
  return { run };
}
