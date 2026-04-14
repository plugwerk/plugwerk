/*
 * Copyright (c) 2025-present devtank42 GmbH
 *
 * This file is part of Plugwerk.
 *
 * Plugwerk is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Plugwerk is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Plugwerk. If not, see <https://www.gnu.org/licenses/>.
 */
import { ErrorPage } from "./ErrorPage";

const Illustration = () => (
  <svg
    viewBox="0 0 160 160"
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
    width="160"
    height="160"
  >
    <rect x="30" y="30" width="60" height="60" rx="6" />
    <path d="M55 30 C55 22 65 22 65 30" />
    <path d="M90 55 C98 55 98 65 90 65" />
    <path d="M55 90 C55 98 65 98 65 90" />
    <rect x="68" y="68" width="56" height="56" rx="6" strokeDasharray="4 3" />
    <circle cx="115" cy="120" r="18" />
    <line x1="128" y1="133" x2="148" y2="153" />
  </svg>
);

export function Error404Page() {
  return (
    <ErrorPage
      code={404}
      title="Page not found"
      message="The page you're looking for doesn't exist or may have been moved."
      illustration={<Illustration />}
    />
  );
}
