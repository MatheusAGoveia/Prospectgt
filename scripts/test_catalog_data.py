import json
import pathlib
import tempfile
import unittest
from catalog_data import combine_catalogs, configuration, resolve_catalog


class CatalogPreparationTest(unittest.TestCase):
    def test_priority_cities_and_source_configuration(self):
        config = configuration()
        self.assertEqual([city["label"] for city in config["cities"][:3]], ["Betim, MG", "Contagem, MG", "Belo Horizonte, MG"])
        self.assertEqual({region["id"] for region in config["regions"]}, {"centro-oeste", "sudeste"})

    def test_merge_preserves_records_deduplicates_osm_keys_and_counts_contacts(self):
        with tempfile.TemporaryDirectory() as folder:
            root = pathlib.Path(folder)
            one, two, polygon = root / "one.jsonl", root / "two.jsonl", root / "test.poly"
            polygon.write_text("Test\n1\n-45 -21\n-43 -21\n-43 -19\n-45 -19\nEND\nEND\n", encoding="utf-8")
            place = {"key": "osm:node:1", "category": "cafes", "phone": "3133334444"}
            one.write_text(json.dumps(place) + "\n", encoding="utf-8")
            two.write_text(json.dumps(place) + "\n" + json.dumps({"key": "osm:node:2", "category": "lojas"}) + "\n", encoding="utf-8")
            output = root / "combined.jsonl"
            meta = combine_catalogs([({"id":"one","name":"One","page":"https://example.com/one"}, one, polygon), ({"id":"two","name":"Two","page":"https://example.com/two"}, two, polygon)], output)
            self.assertEqual(meta["records"], 2)
            self.assertEqual(meta["withContact"], 1)
            self.assertEqual(meta["categories"], {"cafes":1,"lojas":1})
            active = resolve_catalog(output, verify=True)
            self.assertEqual(len(active.read_text().splitlines()), 2)
            self.assertIn("sources", json.loads(active.with_name(active.name + ".meta.json").read_text(encoding="utf-8")))

    def test_failed_activation_keeps_previous_complete_snapshot(self):
        from unittest.mock import patch
        import os
        with tempfile.TemporaryDirectory() as folder:
            root=pathlib.Path(folder)
            source, polygon, output = root / "input.jsonl", root / "test.poly", root / "combined.jsonl"
            polygon.write_text("Test\n1\n-45 -21\n-43 -21\n-43 -19\n-45 -19\nEND\nEND\n", encoding="utf-8")
            source.write_text('{"key":"osm:node:1","category":"cafes"}\n',encoding="utf-8")
            inputs=[({"id":"test","name":"Test","page":"https://example.com"},source,polygon)]
            combine_catalogs(inputs,output)
            previous=resolve_catalog(output,verify=True)
            source.write_text('{"key":"osm:node:2","category":"lojas"}\n',encoding="utf-8")
            replace=os.replace
            def fail_pointer(origin,destination):
                if str(destination).endswith(".snapshot.json"):
                    raise PermissionError("Simulação de arquivo bloqueado")
                return replace(origin,destination)
            with patch("catalog_data.os.replace",side_effect=fail_pointer):
                with self.assertRaises(PermissionError):
                    combine_catalogs(inputs,output)
            self.assertEqual(resolve_catalog(output,verify=True),previous)
            self.assertIn("osm:node:1",previous.read_text())

    def test_failed_input_leaves_published_catalog_unchanged(self):
        with tempfile.TemporaryDirectory() as folder:
            root = pathlib.Path(folder)
            output = root / "combined.jsonl"
            output.write_text("published-before", encoding="utf-8")
            with self.assertRaises(FileNotFoundError):
                combine_catalogs([({"id":"missing"}, root / "missing.jsonl", root / "missing.poly")], output)
            self.assertEqual(output.read_text(), "published-before")


if __name__ == "__main__":
    unittest.main()
