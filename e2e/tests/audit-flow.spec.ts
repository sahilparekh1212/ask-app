import { expect, Page, test } from '@playwright/test';

/**
 * End-to-end flows over the real compose stack. Nothing is mocked: the browser talks to the
 * nginx-served production UI bundle, which proxies /auth-api and /audit-api to the Auth and
 * Audit containers; a demo login publishes an AuditEvent to Redpanda that Audit consumes and
 * persists to Postgres. These tests are the "how do you know the whole thing works?" answer.
 */

/** Logs in via the demo form (prefilled demo/demo) and waits for the post-login redirect. */
async function loginAsDemo(page: Page): Promise<void> {
  await page.goto('/login');
  await expect(page.getByLabel('Username')).toHaveValue('demo');
  await page.getByRole('button', { name: 'Demo login' }).click();
  await page.waitForURL('**/profile');
}

async function openAudit(page: Page): Promise<void> {
  await page.getByRole('link', { name: 'Dashboard' }).click();
  await page.waitForURL('**/audit');
  // The seeder guarantees data on first run, so the initial unfiltered load must show rows.
  await expect(page.locator('tbody tr').first()).toBeVisible();
  await expect(page.locator('.stats .total')).toBeVisible();
}

/** Parses "Page 1 of N · M total" from the pager. */
async function totalElements(page: Page): Promise<number> {
  const text = (await page.locator('.pager span').innerText()).replace(/[,.\s]/g, ' ');
  const match = text.match(/(\d+)\s+total/);
  expect(match, `pager text should contain a total: "${text}"`).not.toBeNull();
  return Number(match![1]);
}

test('demo login signs in and lands on the profile page', async ({ page }) => {
  await loginAsDemo(page);
  await expect(page).toHaveURL(/\/profile$/);
  // Authenticated shell: the nav now offers Profile instead of Sign in.
  await expect(page.getByRole('link', { name: 'Sign in' })).toHaveCount(0);
  await expect(page.getByRole('link', { name: 'Profile' })).toBeVisible();
});

test('a login event flows through Kafka into a queryable audit row', async ({ page }) => {
  await loginAsDemo(page);
  await openAudit(page);

  // The LOGIN row for this session's demo login arrives asynchronously (Auth -> Redpanda ->
  // Audit consumer -> Postgres), so re-apply the filter until it shows up. Details are
  // "demo-user" (AuthController.DEMO_USER_ID), which the details-contains filter can target.
  await page.getByLabel('Details').fill('demo-user');
  await expect
    .poll(
      async () => {
        await page.getByRole('button', { name: 'Apply filters' }).click();
        return page
          .locator('tbody tr')
          .filter({ has: page.locator('td:nth-child(3)', { hasText: 'LOGIN' }) })
          .count();
      },
      {
        message: 'expected the demo LOGIN event to be consumed from Kafka and persisted',
        timeout: 30_000,
      },
    )
    .toBeGreaterThan(0);
});

test('adding demo logs grows the table and the stats', async ({ page }) => {
  await loginAsDemo(page);
  await openAudit(page);

  const before = await totalElements(page);
  await page.getByLabel('Demo logs').fill('7');
  await page.getByRole('button', { name: 'Add demo logs' }).click();
  await expect(page.locator('.demo-tools .note')).toHaveText('Added 7 demo logs.');

  // >= rather than ===: LOGIN events from these very tests may land concurrently.
  await expect
    .poll(() => totalElements(page), { timeout: 15_000 })
    .toBeGreaterThanOrEqual(before + 7);
  await expect(page.locator('.stats .total')).toContainText('matching entries');
});

test('filtering by entity type narrows the rows and the stats agree', async ({ page }) => {
  await loginAsDemo(page);
  await openAudit(page);

  // Options are built from the stats buckets; "User" exists because logins above produced rows.
  const entityType = page.getByLabel('Entity type');
  await expect(entityType.locator('option', { hasText: 'User' })).toHaveCount(1);
  await entityType.selectOption('User');
  await page.getByRole('button', { name: 'Apply filters' }).click();

  // Wait for the filtered reload, then check every visible row matches the filter.
  await expect.poll(() => page.locator('.stats .total').innerText()).toMatch(/^\d+ matching/);
  await expect
    .poll(async () => {
      const cells = await page.locator('tbody tr td:nth-child(2)').allInnerTexts();
      return cells.length > 0 && cells.every((c) => c === 'User');
    })
    .toBe(true);

  // The stats and the pager must agree on what "matching" means — same Specification
  // server-side, so the two totals are asserted equal, not merely both present.
  const statsTotal = Number((await page.locator('.stats .total').innerText()).match(/^(\d+)/)?.[1]);
  expect(statsTotal).toBe(await totalElements(page));
});
