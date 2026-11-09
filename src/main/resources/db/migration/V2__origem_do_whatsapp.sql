ALTER TABLE lead ADD COLUMN source_whatsapp VARCHAR(100);
ALTER TABLE lead ADD COLUMN site_whatsapp VARCHAR(100);
-- Dados antigos permanecem até a próxima leitura da respectiva fonte.
UPDATE lead SET source_whatsapp = whatsapp WHERE whatsapp IS NOT NULL;
