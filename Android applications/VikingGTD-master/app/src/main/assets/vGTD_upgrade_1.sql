ALTER TABLE actions ADD COLUMN  "repeat_type" INTEGER NOT NULL DEFAULT(0);
ALTER TABLE actions ADD COLUMN  "repeat_unit" INTEGER NOT NULL DEFAULT(0);
ALTER TABLE actions ADD COLUMN  "repeat_after" INTEGER NOT NULL DEFAULT(0);
