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
import { ErrorPage } from './ErrorPage'

const Illustration = () => (
  <svg viewBox="0 0 160 160" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" width="160" height="160">
    <rect x="30" y="50" width="100" height="70" rx="8"/>
    <line x1="30" y1="70" x2="130" y2="70"/>
    <circle cx="48" cy="60" r="4"/>
    <circle cx="63" cy="60" r="4"/>
    <line x1="55" y1="95" x2="105" y2="95" strokeDasharray="6 4"/>
    <line x1="55" y1="108" x2="85" y2="108" strokeDasharray="6 4"/>
  </svg>
)

export function Error503Page() {
  return (
    <ErrorPage
      code={503}
      title="Service unavailable"
      message="The server is temporarily unavailable. Please try again later."
      illustration={<Illustration />}
    />
  )
}
