-- A V1 misturava fonte OSM e extração de site. Quando há evidência explícita
-- de link no site, preservar como evidência do site em vez de fixar como OSM.
UPDATE lead
SET site_whatsapp = whatsapp, source_whatsapp = NULL
WHERE site_whatsapp IS NULL
  AND whatsapp IS NOT NULL
  AND analyzed_at IS NOT NULL
  AND REPLACE(analysis_details, ' ', '') LIKE '%"whatsappPublished":true%';
