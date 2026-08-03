import { env } from '../config/env.js';
import { defaultThresholds } from '../config/thresholds.js';
import { buildSummary } from './report.js';

/**
 * Base options every scenario spreads. TLS skip matches Compose self-signed Nginx.
 */
export function baseOptions(extra = {}) {
  const cfg = env();
  return Object.assign(
    {
      insecureSkipTLSVerify: cfg.insecureSkipTLSVerify,
      thresholds: defaultThresholds(),
      summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
    },
    extra,
  );
}

export function constantVus(extra = {}) {
  const cfg = env();
  return baseOptions(
    Object.assign(
      {
        vus: cfg.vus,
        duration: cfg.duration,
      },
      extra,
    ),
  );
}

export function rampingVus(extra = {}) {
  const cfg = env();
  return baseOptions(
    Object.assign(
      {
        stages: [
          { duration: cfg.rampUp, target: cfg.rampVus },
          { duration: cfg.sustain, target: cfg.rampVus },
          { duration: cfg.rampDown, target: 0 },
        ],
      },
      extra,
    ),
  );
}

/** Open-loop redirect-style load: fixed requests/sec, not "as fast as possible". */
export function constantArrivalRate(extra = {}) {
  const cfg = env();
  return baseOptions(
    Object.assign(
      {
        scenarios: {
          default: {
            executor: 'constant-arrival-rate',
            rate: cfg.arrivalRate,
            timeUnit: '1s',
            duration: cfg.duration,
            preAllocatedVUs: Math.min(cfg.vus, cfg.maxVus),
            maxVUs: cfg.maxVus,
          },
        },
      },
      extra,
    ),
  );
}

/** Stress: ramp arrival rate via stages of VUs issuing requests (closed loop). */
export function rampingArrivalRate(extra = {}) {
  const cfg = env();
  return baseOptions(
    Object.assign(
      {
        scenarios: {
          default: {
            executor: 'ramping-arrival-rate',
            startRate: Math.max(1, Math.floor(cfg.arrivalRate / 5)),
            timeUnit: '1s',
            preAllocatedVUs: Math.min(cfg.vus, cfg.maxVus),
            maxVUs: cfg.maxVus,
            stages: [
              { duration: cfg.rampUp, target: cfg.rampRate },
              { duration: cfg.sustain, target: cfg.rampRate },
              { duration: cfg.rampDown, target: 0 },
            ],
          },
        },
      },
      extra,
    ),
  );
}

export function soakVus(extra = {}) {
  const cfg = env();
  return baseOptions(
    Object.assign(
      {
        vus: cfg.soakVus,
        duration: cfg.soakDuration,
      },
      extra,
    ),
  );
}

/** Bind handleSummary for a named scenario. */
export function summaryHandler(scenario) {
  return function handleSummary(data) {
    return buildSummary(data, { scenario });
  };
}
