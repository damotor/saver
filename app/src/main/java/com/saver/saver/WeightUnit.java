/*
Copyright 2018 Daniel Monedero-Tortola

This file is part of Saver.

Saver is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Saver is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Saver.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.saver.saver;

public enum WeightUnit {
    KILOGRAMS("kilograms"),
    POUNDS("pounds")
    ;

    private final String name;

    private WeightUnit(final String name) {
        this.name = name;
    }

    public static WeightUnit fromName(String name) {
        if (name.equals(KILOGRAMS.toString())) {
            return KILOGRAMS;
        } else {
            return POUNDS;
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
