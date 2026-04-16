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
import { useState, useEffect, useCallback } from "react";
import {
  Box,
  Typography,
  Button,
  Divider,
  CircularProgress,
} from "@mui/material";
import { Pencil, Plus, Trash2 } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { DataTable } from "../common/DataTable";
import type { DataColumn } from "../common/DataTable";
import { ActionIconButton } from "../common/ActionIconButton";
import { namespacesApi } from "../../api/config";
import type { NamespaceSummary } from "../../api/generated/model";
import { CreateNamespaceDialog } from "./CreateNamespaceDialog";
import { DeleteNamespaceDialog } from "./DeleteNamespaceDialog";
import { useNamespaceStore } from "../../stores/namespaceStore";
import { useAuthStore } from "../../stores/authStore";
import { useUiStore } from "../../stores/uiStore";

export function NamespacesSection() {
  const [namespaces, setNamespaces] = useState<NamespaceSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [createOpen, setCreateOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<NamespaceSummary | null>(
    null,
  );
  const navigate = useNavigate();
  const { addToast } = useUiStore();

  const loadNamespaces = useCallback(async () => {
    setLoading(true);
    try {
      const res = await namespacesApi.listNamespaces();
      setNamespaces(res.data);
    } catch {
      setNamespaces([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadNamespaces();
  }, [loadNamespaces]);

  function handleCreated(ns: NamespaceSummary) {
    setNamespaces((prev) => [...prev, ns]);
    addToast({ message: `Namespace "${ns.slug}" created.`, type: "success" });

    // Refresh the global namespace dropdown in the header
    useNamespaceStore.getState().fetchNamespaces();
  }

  function handleDeleted(slug: string) {
    const remaining = namespaces.filter((n) => n.slug !== slug);
    setNamespaces(remaining);
    setDeleteTarget(null);
    addToast({ message: `Namespace "${slug}" deleted.`, type: "success" });

    // Refresh the global namespace dropdown in the header
    useNamespaceStore.getState().fetchNamespaces();

    // If the deleted namespace was currently selected, switch to the next available
    const { namespace: current, setNamespace } = useAuthStore.getState();
    if (current === slug) {
      const next = remaining[0]?.slug;
      if (next) {
        setNamespace(next);
      }
    }
  }

  const namespaceCols: DataColumn<NamespaceSummary>[] = [
    {
      key: "slug",
      header: "Slug",
      render: (ns) => (
        <Typography variant="body2" fontWeight={500}>
          {ns.slug}
        </Typography>
      ),
    },
    {
      key: "name",
      header: "Name",
      render: (ns) => (
        <Typography variant="caption" color="text.secondary">
          {ns.name || "\u2014"}
        </Typography>
      ),
    },
    {
      key: "actions",
      header: "",
      align: "right",
      render: (ns) => (
        <Box sx={{ display: "flex", gap: 0.5, justifyContent: "flex-end" }}>
          <ActionIconButton
            icon={Pencil}
            tooltip="Edit"
            onClick={() => navigate(`/admin/namespaces/${ns.slug}`)}
          />
          <ActionIconButton
            icon={Trash2}
            tooltip="Delete"
            color="error"
            onClick={() => setDeleteTarget(ns)}
          />
        </Box>
      ),
    },
  ];

  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 3 }}>
      <Box
        sx={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
        }}
      >
        <Box>
          <Typography variant="h2" gutterBottom>
            Namespaces
          </Typography>
          <Divider sx={{ mb: 3 }} />
        </Box>
        <Button
          variant="outlined"
          size="small"
          startIcon={<Plus size={14} />}
          onClick={() => setCreateOpen(true)}
        >
          Create Namespace
        </Button>
      </Box>

      {loading ? (
        <Box sx={{ display: "flex", justifyContent: "center", py: 4 }}>
          <CircularProgress size={24} />
        </Box>
      ) : namespaces.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          No namespaces found.
        </Typography>
      ) : (
        <DataTable<NamespaceSummary>
          ariaLabel="Namespaces"
          rows={namespaces}
          keyFn={(ns) => ns.slug}
          columns={namespaceCols}
        />
      )}

      <CreateNamespaceDialog
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={handleCreated}
        onError={(msg) => addToast({ message: msg, type: "error" })}
      />

      <DeleteNamespaceDialog
        namespace={deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onDeleted={handleDeleted}
        onError={(msg) => addToast({ type: "error", message: msg })}
      />
    </Box>
  );
}
