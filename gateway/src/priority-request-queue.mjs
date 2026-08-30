import { REQUEST_PRIORITY } from './provider-key-pool.mjs';

/** Small FIFO admission queue with explicit paid-first ordering. */
export class PriorityRequestQueue {
  constructor() {
    this.queues = new Map([
      [REQUEST_PRIORITY.AI_PLUS, []],
      [REQUEST_PRIORITY.PRO, []],
      [REQUEST_PRIORITY.FREE, []],
    ]);
  }

  enqueue(priority, value) {
    const queue = this.queues.get(priority);
    if (!queue) throw new Error(`unknown request priority: ${priority}`);
    queue.push(value);
  }

  dequeue() {
    for (const priority of [
      REQUEST_PRIORITY.AI_PLUS,
      REQUEST_PRIORITY.PRO,
      REQUEST_PRIORITY.FREE,
    ]) {
      const queue = this.queues.get(priority);
      if (queue.length > 0) {
        return { priority, value: queue.shift() };
      }
    }
    return null;
  }

  get size() {
    let total = 0;
    for (const queue of this.queues.values()) total += queue.length;
    return total;
  }
}
