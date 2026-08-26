import { useEffect, useRef, useState, useCallback } from "react";
import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import type { LeaderboardUpdateMessage, LeaderboardEntry } from "../types";

export type ConnectionStatus = "LIVE" | "RECONNECTING" | "OFFLINE";

interface UseWebSocketOptions {
  sport: string;
  onMessage: (msg: LeaderboardUpdateMessage) => void;
}

export function useWebSocket({ sport, onMessage }: UseWebSocketOptions) {
  const [status, setStatus] = useState<ConnectionStatus>("OFFLINE");
  const clientRef = useRef<Client | null>(null);
  const subscriptionRef = useRef<StompSubscription | null>(null);
  const onMessageRef = useRef(onMessage);
  const reconnectDelay = useRef(1000);

  onMessageRef.current = onMessage;

  const subscribe = useCallback(
    (client: Client, sportCode: string) => {
      if (subscriptionRef.current) {
        subscriptionRef.current.unsubscribe();
      }
      const topic = `/topic/leaderboards/${sportCode.toLowerCase()}`;
      subscriptionRef.current = client.subscribe(topic, (message: IMessage) => {
        try {
          const data: LeaderboardUpdateMessage = JSON.parse(message.body);
          onMessageRef.current(data);
        } catch {
          // malformed message — ignore
        }
      });
    },
    [],
  );

  useEffect(() => {
    const wsUrl =
      import.meta.env.VITE_WS_URL || "http://localhost:8080/ws";

    const client = new Client({
      webSocketFactory: () => new SockJS(wsUrl),
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        setStatus("LIVE");
        reconnectDelay.current = 1000;
        subscribe(client, sport);
      },
      onDisconnect: () => {
        setStatus("OFFLINE");
      },
      onStompError: () => {
        setStatus("RECONNECTING");
      },
      onWebSocketClose: () => {
        setStatus("RECONNECTING");
      },
    });

    client.activate();
    clientRef.current = client;

    return () => {
      if (subscriptionRef.current) {
        subscriptionRef.current.unsubscribe();
        subscriptionRef.current = null;
      }
      client.deactivate();
      clientRef.current = null;
      setStatus("OFFLINE");
    };
  }, [sport, subscribe]);

  return { status };
}
