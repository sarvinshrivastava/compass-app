# The Vercel project answers on two hostnames and only one of them is public

> **Rule:** the public URL is `https://minimal-compass.vercel.app` — never the `-sarvinshrivastavas-projects.vercel.app` form, which 302s to a Vercel SSO login.
> **Applies to:** `site/.gitignore`

Found 2026-09-13 deploying the privacy-policy change that Play's Data Safety
form has to agree with.

## What happened

`vercel --prod` succeeded, and then every path on the URL that was written into
the launch docs started answering `302`:

```
$ curl -s -o /dev/null -w "%{http_code} %{redirect_url}\n" \
    https://minimal-compass-sarvinshrivastavas-projects.vercel.app/privacy
302 https://vercel.com/sso-api?url=...&nonce=...
```

`vercel inspect` shows the deployment carries two aliases:

```
Aliases
  ╶ https://minimal-compass.vercel.app
  ╶ https://minimal-compass-sarvinshrivastavas-projects.vercel.app
```

The short one returns `200`. The long one is covered by Vercel's **Standard
Deployment Protection**, which applies to the `-<team-slug>.vercel.app`
deployment-URL class while leaving the production alias public.

## Why it matters here

A privacy policy behind an SSO redirect fails Google Play's check outright, and
the failure is invisible from a browser that is already signed in to Vercel —
the page just loads. Always check with `curl`, from the command line, not by
clicking the link:

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://minimal-compass.vercel.app/privacy   # want 200
```

## Also

- `site/.gitignore` lists `.env*`, but the Vercel CLI's upload rules are its
  own. A `.vercelignore` was added so a local env file cannot be uploaded —
  this is a **static** site, so anything in the directory is served at its own
  path.
- The CLI account (`vercel whoami` → `arctic-player`) is not the account the
  Vercel MCP connector authenticates as; the connector returns `403 Forbidden`
  for this project. Use the CLI for anything touching this project.
