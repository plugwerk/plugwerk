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
import { describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TimezoneSelect } from "./TimezoneSelect";
import { renderWithTheme } from "../../test/renderWithTheme";

describe("TimezoneSelect", () => {
  it("renders the label for the currently selected zone", () => {
    renderWithTheme(
      <TimezoneSelect value="Europe/Berlin" onChange={() => {}} />,
    );
    const input = screen.getByRole("combobox") as HTMLInputElement;
    expect(input.value).toMatch(/Europe\/Berlin/);
  });

  it("leaves the input empty for an unknown value", () => {
    renderWithTheme(
      <TimezoneSelect value="Not/AZone" onChange={() => {}} allowEmpty />,
    );
    const input = screen.getByRole("combobox") as HTMLInputElement;
    expect(input.value).toBe("");
  });

  it("emits the chosen zone id via onChange when an option is picked", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    renderWithTheme(<TimezoneSelect value="UTC" onChange={onChange} />);

    const input = screen.getByRole("combobox");
    await user.clear(input);
    await user.type(input, "Europe/Berlin");
    const option = await screen.findByRole("option", {
      name: /Europe\/Berlin/,
    });
    await user.click(option);

    expect(onChange).toHaveBeenCalledWith("Europe/Berlin");
  });

  it("offers the empty sentinel option when allowEmpty is set", async () => {
    const user = userEvent.setup();
    renderWithTheme(
      <TimezoneSelect
        value="Europe/Berlin"
        onChange={() => {}}
        allowEmpty
        emptyLabel="Use system default"
      />,
    );

    await user.click(screen.getByRole("combobox"));
    expect(
      await screen.findByRole("option", { name: "Use system default" }),
    ).toBeInTheDocument();
  });

  it("renders the default Timezone label and is clearable-free without allowEmpty", () => {
    renderWithTheme(<TimezoneSelect value="UTC" onChange={() => {}} />);
    // disableClearable is true here, so no clear button is rendered.
    expect(screen.queryByLabelText("Clear")).not.toBeInTheDocument();
    expect(screen.getByLabelText(/Timezone/)).toBeInTheDocument();
  });
});
