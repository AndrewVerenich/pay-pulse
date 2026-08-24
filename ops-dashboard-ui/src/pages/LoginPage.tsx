import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useNavigate } from "react-router-dom";
import { Activity } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Card } from "@/components/ui/Card";

const schema = z.object({
  username: z.string().min(3, "Min 3 characters"),
  password: z.string().min(5, "Min 5 characters"),
});

type FormValues = z.infer<typeof schema>;

export function LoginPage() {
  const navigate = useNavigate();
  const { login } = useAuth();
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { username: "admin", password: "admin" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setServerError(null);
    try {
      await login(values.username, values.password);
      navigate("/live", { replace: true });
    } catch (e) {
      setServerError(e instanceof Error ? e.message : "Login failed");
    }
  });

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <Card className="w-full max-w-sm p-8">
        <div className="mb-6 flex items-center gap-2 text-xl font-semibold">
          <Activity className="h-6 w-6 text-indigo-400" />
          PayPulse <span className="text-slate-500">Ops</span>
        </div>
        <form onSubmit={onSubmit} className="space-y-4">
          <div>
            <label className="mb-1 block text-sm text-slate-400">Username</label>
            <Input autoFocus {...register("username")} />
            {errors.username && <p className="mt-1 text-xs text-rose-400">{errors.username.message}</p>}
          </div>
          <div>
            <label className="mb-1 block text-sm text-slate-400">Password</label>
            <Input type="password" {...register("password")} />
            {errors.password && <p className="mt-1 text-xs text-rose-400">{errors.password.message}</p>}
          </div>
          {serverError && (
            <p className="rounded-md border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-sm text-rose-300">
              {serverError}
            </p>
          )}
          <Button type="submit" disabled={isSubmitting} className="w-full">
            {isSubmitting ? "Signing in…" : "Sign in"}
          </Button>
        </form>
      </Card>
    </div>
  );
}
