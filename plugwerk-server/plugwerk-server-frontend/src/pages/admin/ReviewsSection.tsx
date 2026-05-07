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
import { useState, useEffect } from "react";
import { Box, Typography, Divider, CircularProgress } from "@mui/material";
import { CheckCircle, XCircle } from "lucide-react";
import { DataTable } from "../../components/common/DataTable";
import type { DataColumn } from "../../components/common/DataTable";
import { ActionIconButton } from "../../components/common/ActionIconButton";
import { Timestamp } from "../../components/common/Timestamp";
import { reviewsApi } from "../../api/config";
import { pluginsKeys } from "../../api/hooks/usePlugins";
import { useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "../../stores/authStore";
import { useUiStore } from "../../stores/uiStore";
import type { ReviewItemDto } from "../../api/generated/model";

export function ReviewsSection() {
  const namespace = useAuthStore((s) => s.namespace);
  const [items, setItems] = useState<ReviewItemDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [approvingId, setApprovingId] = useState<string | null>(null);
  const [rejectingId, setRejectingId] = useState<string | null>(null);
  const addToast = useUiStore((s) => s.addToast);
  const queryClient = useQueryClient();

  useEffect(() => {
    async function load() {
      setLoading(true);
      try {
        if (!namespace) return;
        const res = await reviewsApi.listPendingReviews({ ns: namespace });
        setItems(res.data);
      } catch {
        setItems([]);
      } finally {
        setLoading(false);
      }
    }
    load();
  }, [namespace]);

  async function handleApprove(item: ReviewItemDto) {
    if (!namespace) return;
    setApprovingId(item.releaseId);
    try {
      await reviewsApi.approveRelease({
        ns: namespace,
        releaseId: item.releaseId,
      });
      queryClient.invalidateQueries({
        queryKey: pluginsKeys.namespace(namespace),
      });
      setItems((prev) => prev.filter((i) => i.releaseId !== item.releaseId));
      addToast({
        message: `${item.pluginName} v${item.version} approved and published.`,
        type: "success",
      });
    } catch {
      addToast({
        message: `Failed to approve ${item.pluginName} v${item.version}.`,
        type: "error",
      });
    } finally {
      setApprovingId(null);
    }
  }

  async function handleReject(item: ReviewItemDto) {
    if (!namespace) return;
    setRejectingId(item.releaseId);
    try {
      await reviewsApi.rejectRelease({
        ns: namespace,
        releaseId: item.releaseId,
      });
      queryClient.invalidateQueries({
        queryKey: pluginsKeys.namespace(namespace),
      });
      setItems((prev) => prev.filter((i) => i.releaseId !== item.releaseId));
      addToast({
        message: `${item.pluginName} v${item.version} rejected.`,
        type: "success",
      });
    } catch {
      addToast({
        message: `Failed to reject ${item.pluginName} v${item.version}.`,
        type: "error",
      });
    } finally {
      setRejectingId(null);
    }
  }

  const reviewColumns: DataColumn<ReviewItemDto>[] = [
    {
      key: "plugin",
      header: "Plugin",
      render: (item) => (
        <>
          <Typography
            variant="body2"
            sx={{
              fontWeight: 500,
            }}
          >
            {item.pluginName}
          </Typography>
          <Typography
            variant="caption"
            sx={{
              color: "text.secondary",
            }}
          >
            {item.pluginId}
          </Typography>
        </>
      ),
    },
    {
      key: "namespace",
      header: "Namespace",
      render: () => (
        <Typography
          variant="caption"
          sx={{
            color: "text.secondary",
          }}
        >
          {namespace}
        </Typography>
      ),
    },
    {
      key: "version",
      header: "Version",
      render: (item) => <>v{item.version}</>,
    },
    {
      key: "submitted",
      header: "Submitted",
      render: (item) => (
        <Typography
          variant="caption"
          sx={{
            color: "text.disabled",
          }}
        >
          <Timestamp date={item.submittedAt} />
        </Typography>
      ),
    },
    {
      key: "actions",
      header: "Actions",
      render: (item) => (
        <Box sx={{ display: "flex", gap: 0.5 }}>
          <ActionIconButton
            icon={CheckCircle}
            tooltip="Approve"
            color="success"
            loading={approvingId === item.releaseId}
            onClick={() => handleApprove(item)}
          />
          <ActionIconButton
            icon={XCircle}
            tooltip="Deny"
            color="error"
            loading={rejectingId === item.releaseId}
            onClick={() => handleReject(item)}
          />
        </Box>
      ),
    },
  ];

  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 3 }}>
      <Box>
        <Typography variant="h2" gutterBottom>
          Pending Reviews
        </Typography>
        <Divider sx={{ mb: 3 }} />
      </Box>
      {loading ? (
        <Box sx={{ display: "flex", justifyContent: "center", py: 4 }}>
          <CircularProgress size={24} />
        </Box>
      ) : items.length === 0 ? (
        <Typography
          variant="body2"
          sx={{
            color: "text.secondary",
          }}
        >
          No releases awaiting review.
        </Typography>
      ) : (
        <DataTable<ReviewItemDto>
          columns={reviewColumns}
          rows={items}
          keyFn={(item) => item.releaseId}
          ariaLabel="Pending reviews"
        />
      )}
    </Box>
  );
}
