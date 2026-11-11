import { useEffect, useRef, useState } from "react";
import { money } from "../utils/helpers";

export default function AnimatedMoney({ value, prefix = "$" }: { value: number; prefix?: string }) {
  const [display, setDisplay] = useState(value);
  const prev = useRef(value);
  useEffect(() => {
    if (prev.current === value) return;
    const from = prev.current;
    prev.current = value;
    const dur = 600, start = performance.now();
    let raf: number;
    const tick = (now: number) => {
      const t = Math.min((now - start) / dur, 1);
      setDisplay(from + (value - from) * (1 - Math.pow(1 - t, 3)));
      if (t < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [value]);
  return <>{prefix}{money(display)}</>;
}
