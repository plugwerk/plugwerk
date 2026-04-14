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
    <circle cx="80" cy="80" r="50" />
    <line x1="80" y1="52" x2="80" y2="90" />
    <circle cx="80" cy="108" r="4" fill="currentColor" stroke="none" />
  </svg>
);

export function Error500Page() {
  return (
    <ErrorPage
      code={500}
      title="Internal server error"
      message="Something went wrong on our end. Please try again in a moment."
      illustration={<Illustration />}
    />
  );
}
