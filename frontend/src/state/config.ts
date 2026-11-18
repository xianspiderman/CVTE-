import { create } from "zustand";
import { get, put, type Config } from "../api/business";
interface State {
  config?: Config;
  error: string;
  load: () => Promise<void>;
  save: (c: Config) => Promise<void>;
}
export const useConfig = create<State>((set) => ({
  error: "",
  load: async () => {
    try {
      set({ config: await get<Config>("/workbench/config"), error: "" });
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },
  save: async (c) => {
    const saved = await put<Config>("/workbench/config", c);
    set({ config: saved, error: "" });
  },
}));
