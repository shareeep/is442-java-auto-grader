import * as React from "react"
import * as TabsPrimitive from "@radix-ui/react-tabs"
import { cn } from "@/lib/utils"
import { CopyButton } from "@/components/ui/code-editor"

type CodeTabsProps = {
  codes: Record<string, string>
  lang?: string
  themes?: {
    light: string
    dark: string
  }
  copyButton?: boolean
  onCopy?: (content: string) => undefined | boolean
  className?: string
  defaultValue?: string
  value?: string
  onValueChange?: (value: string) => void
}

const CodeTabsContent = React.memo(function CodeTabsContent({
  codes,
  lang = "bash",
  themes = { light: "github-light", dark: "github-light" },
  copyButton = true,
  onCopy,
  activeValue,
}: {
  codes: Record<string, string>
  lang?: string
  themes?: { light: string; dark: string }
  copyButton?: boolean
  onCopy?: (content: string) => undefined | boolean
  activeValue: string
}) {
  const codeEntries = React.useMemo(() => Object.entries(codes), [codes])
  const codeKeys = React.useMemo(() => Object.keys(codes), [codes])

  const [highlightedCodes, setHighlightedCodes] = React.useState<Record<string, string>>(codes)

  React.useEffect(() => {
    let cancelled = false
    async function highlight() {
      try {
        const { codeToHtml } = await import("shiki")
        const results = await Promise.all(
          Object.entries(codes).map(async ([key, val]) => {
            const html = await codeToHtml(val, {
              lang,
              themes: { light: themes.light, dark: themes.dark },
              defaultColor: "light",
            })
            return [key, html] as const
          })
        )
        if (!cancelled) {
          setHighlightedCodes(Object.fromEntries(results))
        }
      } catch (e) {
        console.error("shiki highlight error", e)
      }
    }
    const raf = requestAnimationFrame(() => highlight())
    return () => { cancelled = true; cancelAnimationFrame(raf) }
  }, [codes, lang, themes.light, themes.dark])

  return (
    <>
      <TabsPrimitive.List className="w-full relative flex items-center justify-between h-10 bg-muted border-b border-border/75 px-4">
        <div className="flex gap-x-3 h-full">
          {codeKeys.map(key => (
            <TabsPrimitive.Trigger
              key={key}
              value={key}
              className={cn(
                "text-foreground/60 data-[state=active]:text-foreground px-0 h-full border-b-2 border-transparent data-[state=active]:border-foreground text-[13px] font-medium transition-colors"
              )}
            >
              {key}
            </TabsPrimitive.Trigger>
          ))}
        </div>
        {copyButton && (
          <CopyButton
            className="-me-2 bg-transparent hover:bg-black/5"
            content={codes[activeValue] ?? ""}
            onCopy={onCopy}
            size="sm"
            variant="ghost"
          />
        )}
      </TabsPrimitive.List>

      {codeEntries.map(([key, rawCode]) => (
        <TabsPrimitive.Content
          key={key}
          value={key}
          className="w-full text-sm p-4 overflow-auto flex-1 min-h-0"
        >
          <div className="w-full [&>pre]:m-0 [&>pre]:p-0 [&>pre]:bg-transparent! [&>pre]:border-none [&>pre]:text-[13px] [&>pre]:leading-relaxed [&_code]:text-[13px] [&_code]:leading-relaxed [&_code]:bg-transparent! [&_.shiki]:bg-transparent!">
            {highlightedCodes[key] !== rawCode ? (
              <div dangerouslySetInnerHTML={{ __html: highlightedCodes[key] }} />
            ) : (
              <pre><code>{rawCode}</code></pre>
            )}
          </div>
        </TabsPrimitive.Content>
      ))}
    </>
  )
})

const CodeTabs = React.memo(function CodeTabs({
  codes,
  lang = "bash",
  themes = { light: "github-light", dark: "github-light" },
  className,
  defaultValue,
  value,
  onValueChange,
  copyButton = true,
  onCopy,
}: CodeTabsProps) {
  const firstKey = React.useMemo(() => Object.keys(codes)[0] ?? "", [codes])
  const [internalValue, setInternalValue] = React.useState(defaultValue ?? firstKey)
  const activeValue = value ?? internalValue

  const memoizedThemes = React.useMemo(() => themes, [themes.light, themes.dark])

  return (
    <TabsPrimitive.Root
      value={activeValue}
      onValueChange={v => { setInternalValue(v); onValueChange?.(v) }}
      className={cn("w-full flex flex-col gap-0 bg-background rounded-xl border overflow-hidden", className)}
    >
      <CodeTabsContent
        codes={codes}
        lang={lang}
        themes={memoizedThemes}
        copyButton={copyButton}
        onCopy={onCopy}
        activeValue={activeValue}
      />
    </TabsPrimitive.Root>
  )
})

export { CodeTabs, type CodeTabsProps }
