/**
 * Manual fetch wrapper for the template upload endpoint.
 *
 * `uploadTemplate` cannot use the generated SDK in `src/generated/sdk.gen.ts`
 * because the browser's `webkitdirectory` picker gives each file a
 * `webkitRelativePath` like `RenameToYourUsername/Q1/Solution.java`, but
 * multipart filenames are flat strings — slashes get stripped. To preserve the
 * directory structure, this function encodes each `/` as `__SEP__` before
 * appending to FormData (e.g. `RenameToYourUsername__SEP__Q1__SEP__Solution.java`).
 * The BE splits on `__SEP__` to reconstruct the tree when writing to a temp dir.
 * This encoding logic cannot be expressed in an OpenAPI spec or generated SDK.
 *
 * All other endpoints — including `uploadExam` and `uploadTesters` — are now
 * handled by the generated SDK after the BE multipart annotations were fixed.
 */
import type { UploadDirResponse } from "../types";

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`HTTP ${res.status}: ${body}`);
  }
  return res.json();
}

export async function uploadTemplate(files: File[]): Promise<UploadDirResponse> {
  const form = new FormData();
  for (const file of files) {
    // Encode the relative path using __SEP__ so the server can reconstruct directories
    const encodedName = file.webkitRelativePath.replace(/\//g, "__SEP__");
    form.append("files", file, encodedName);
  }
  const res = await fetch(`/api/generation/template/upload`, {
    method: "POST",
    body: form,
  });
  return json(res);
}
